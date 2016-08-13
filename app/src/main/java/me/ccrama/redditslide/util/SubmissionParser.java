package me.ccrama.redditslide.util;

import android.content.Context;
import android.util.Base64;

import com.google.gson.stream.JsonReader;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import me.ccrama.redditslide.Authentication;
import me.ccrama.redditslide.MMMData;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Utility methods to transform html received from Reddit into a more parsable
 * format.
 *
 * The output will unescape all html, except for table tags and some special delimiter
 * token such as for code blocks.
 */
public class SubmissionParser {
    private static final byte[] magic = "Salted__".getBytes(US_ASCII);

    private static final Pattern SPOILER_PATTERN = Pattern.compile("<a[^>]*title=\"([^\"]*)\"[^>]*>([^<]*)</a>");
    private static final Pattern ENCRYPTION_REGEX = Pattern.compile("<a href=\"/r/MegaMegaMonitor/wiki/encrypted\" title=\"([-\\d]+):([A-Za-z0-9+/=]+)\">(.*?)</a>");
    private static final String TABLE_START_TAG = "<table>";
    private static final String HR_TAG = "<hr/>";
    private static final String TABLE_END_TAG = "</table>";

    private SubmissionParser(Context context) {
    }

    /**
     * Parses html and returns a list corresponding to blocks of text to be
     * formatted.
     *
     * Each block is one of:
     *  - Vanilla text
     *  - Code block
     *  - Table
     *
     * Note that this method will unescape html entities, so this is best called
     * with the raw html received from reddit.
     *
     * @param html html to be formatted. Can be raw from the api
     * @return list of text blocks
     */
    public static List<String> getBlocks(Context c, String html) {
        html = StringEscapeUtils.unescapeHtml4(html)
                .replace("<p>", "<div>")
                .replace("</p>", "</div>")
                .replace("<li>\\s*<div>", "<li>")
                .replace("</div>\\s*</li>", "</li>")
                .replace("<li><div>", "<li>")
                .replace("</div></li>", "</li>")
                .replace("<del>", "[[d[")
                .replace("<sup>", "<sup><small>")
                .replace("</sup>", "</small></sup>")
                .replace("</del>", "]d]]");

        if (html.contains("\n")) {
            html = html.substring(0, html.lastIndexOf("\n"));
        }

        if (html.contains("<!-- SC_ON -->")) {
            html = html.substring(15, html.lastIndexOf("<!-- SC_ON -->"));
        }
        html = parseMMMEncryption(c, html);
        html = parseSpoilerTags(html);
        if (html.contains("<ol") || html.contains("<ul")) {
            html = parseLists(html);
        }



        List<String> codeBlockSeperated = parseCodeTags(html);

        if (html.contains(HR_TAG)) {
            codeBlockSeperated = parseHR(codeBlockSeperated);
        }

        if (html.contains("<table")) {
            return parseTableTags(codeBlockSeperated);
        } else {
            return codeBlockSeperated;
        }
    }

    private static String parseLists(String html) {
        int firstIndex;
        boolean isNumbered;
        int firstOl = html.indexOf("<ol");
        int firstUl = html.indexOf("<ul");

        if ((firstUl != -1 && firstOl > firstUl) || firstOl == -1) {
            firstIndex = firstUl;
            isNumbered = false;
        } else {
            firstIndex = firstOl;
            isNumbered = true;
        }
        List<Integer> listNumbers = new ArrayList<>();
        int indent = -1;

        int i = firstIndex;
        while (i < html.length() - 4 && i != -1) {
            if (html.substring(i, i + 3).equals("<ol") || html.substring(i, i + 3).equals("<ul")) {
                if (html.substring(i, i + 3).equals("<ol")) {
                    isNumbered = true;
                    indent++;
                    listNumbers.add(indent, 1);
                } else {
                    isNumbered = false;
                }
                i = html.indexOf("<li", i);
            } else if (html.substring(i, i + 3).equals("<li")) {
                int tagEnd = html.indexOf(">", i);
                int itemClose = html.indexOf("</li", tagEnd);
                int ulClose = html.indexOf("<ul", tagEnd);
                int olClose = html.indexOf("<ol", tagEnd);
                int closeTag;

                // Find what is closest: </li>, <ul>, or <ol>
                if (((ulClose == -1 && itemClose != -1) || (itemClose != -1 && ulClose != -1 && itemClose < ulClose)) && ((olClose == -1 && itemClose != -1) || (itemClose != -1 && olClose != -1 && itemClose < olClose))) {
                    closeTag = itemClose;
                } else if (((ulClose == -1 && olClose != -1) || (olClose != -1 && ulClose != -1 && olClose < ulClose)) && ((olClose == -1 && itemClose != -1) || (olClose != -1 && itemClose != -1 && olClose < itemClose))) {
                    closeTag = olClose;
                } else {
                    closeTag = ulClose;
                }

                String text = html.substring(tagEnd + 1, closeTag);
                String indentSpacing = "";
                for (int j = 0; j < indent; j++) {
                    indentSpacing += "&nbsp;&nbsp;&nbsp;&nbsp;";
                }
                if (isNumbered) {
                    html = html.substring(0, tagEnd + 1)
                            + indentSpacing +
                            listNumbers.get(indent)+ ". " +
                            text + "<br/>" +
                            html.substring(closeTag);
                    listNumbers.set(indent, listNumbers.get(indent) + 1);
                    i = closeTag + 3;
                } else {
                    html = html.substring(0, tagEnd + 1) + indentSpacing + "• " + text + "<br/>" + html.substring(closeTag);
                    i = closeTag + 2;
                }
            } else {
                i = html.indexOf("<", i + 1);
                if (i != -1 && html.substring(i, i + 4).equals("</ol")) {
                    indent--;
                    if(indent == -1){
                        isNumbered = false;
                    }
                }
            }
        }

        html = html.replace("<ol>","").replace("<ul>","").replace("<li>","").replace("</li>","").replace("</ol>", "").replace("</ul>",""); //Remove the tags, which actually work in Android 7.0 on

        return html;
    }

    private static List<String> parseHR(List<String> blocks) {
        List<String> newBlocks = new ArrayList<>();
        for (String block : blocks) {
            if (block.contains(HR_TAG)) {
                for(String s : block.split(HR_TAG)) {
                    newBlocks.add(s);
                    newBlocks.add(HR_TAG);
                }
                newBlocks.remove(newBlocks.size() - 1);
            } else {
                newBlocks.add(block);
            }
        }

        return newBlocks;
    }

    /**
     * For code within <code>&lt;pre&gt;</code> tags, line breaks are converted to
     * <code>&lt;br /&gt;</code> tags, and spaces to &amp;nbsp;. This allows for Html.fromHtml
     * to preserve indents of these blocks.
     * <p/>
     * In addition, <code>[[&lt;[</code> and <code>]&gt;]]</code> are inserted to denote the
     * beginning and end of code segments, for styling later.
     *
     * @param html the unparsed HTML
     * @return the code parsed HTML with additional markers, split but code blocks
     */
    private static List<String> parseCodeTags(String html) {
        final String startTag = "<pre><code>";
        final String endTag = "</code></pre>";
        String[] startSeperated = html.split(startTag);
        List<String> preSeperated = new ArrayList<>();

        String text;
        String code;
        String[] split;

        preSeperated.add(startSeperated[0].replace("<code>", "<code>[[&lt;[").replace("</code>", "]&gt;]]</code>"));
        for (int i = 1; i < startSeperated.length; i++) {
            text = startSeperated[i];
            split = text.split(endTag);
            code = split[0];
            code = code.replace("\n", "<br/>");
            code = code.replace(" ", "&nbsp;");

            preSeperated.add(startTag + "[[&lt;[" + code + "]&gt;]]" + endTag);
            if (split.length > 1) {
                preSeperated.add(split[1].replace("<code>", "<code>[[&lt;[").replace("</code>", "]&gt;]]</code>"));
            }
        }

        return preSeperated;
    }


    private static String parseMMMEncryption(Context c, String html) {
        String encryptionText;
        String publicText;
        String password = null;
        int subid = 0;
        int keyid;

        Matcher matcher = ENCRYPTION_REGEX.matcher(html);

        while (matcher.find()) {
            keyid = Integer.parseInt(matcher.group(1));
            encryptionText = matcher.group(2);
            publicText = matcher.group(3);
            if (MMMData.loggedin) {
                if(MMMData.data != null) {
                    try {
                        JSONArray subreddits = MMMData.data.getJSONArray("subs");
                        for (int i = 0; i < subreddits.length(); i++) {
                            JSONArray keys = subreddits.getJSONObject(i).getJSONArray("cryptokeys");
                            for (int j = 0; j < keys.length(); j++) {
                                JSONArray keyentry = keys.getJSONArray(j);
                                if (keyentry.getInt(0) == keyid) {
                                    password = keyentry.getString(1);
                                    subid = subreddits.getJSONObject(i).getInt("id");
                                }

                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                } else {
                    JsonReader reader = null;
                    try {
                        reader = new JsonReader(
                                new InputStreamReader(c.openFileInput(Authentication.name+"-mmmdata.json"), "UTF-8"));
                        reader.beginObject();
                        out:
                        while (reader.hasNext()) {
                            if (reader.nextName().equals("subs")) {
                                reader.beginArray();
                                while (reader.hasNext()) {
                                    reader.beginObject();
                                    while (reader.hasNext()) {
                                        String name = reader.nextName();
                                        if (name.equals("cryptokeys")) {
                                            reader.beginArray();
                                            while (reader.hasNext()) {
                                                reader.beginArray();
                                                if (reader.nextInt() == keyid) {
                                                    password = reader.nextString();
                                                } else {
                                                    reader.skipValue();
                                                }
                                                reader.endArray();
                                            }
                                            reader.endArray();
                                        } else if(name.equals("id")) {
                                            subid = reader.nextInt();
                                        } else {
                                            reader.skipValue();
                                        }
                                    }
                                    reader.endObject();
                                    if(password != null) {
                                        reader.close();
                                        break out;
                                    }
                                }
                                reader.endArray();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (password != null) {
                    try {
                        final byte[] inBytes = Base64.decode(encryptionText, 0);

                        final byte[] shouldBeMagic = Arrays.copyOfRange(inBytes, 0,
                                magic.length);
                        if (!Arrays.equals(shouldBeMagic, magic)) {
                            System.out.println("Bad magic number");
                            return html;
                        }

                        final byte[] salt = Arrays.copyOfRange(inBytes, magic.length,
                                magic.length + 8);

                        final byte[] passAndSalt = MMMData.concat(password.getBytes(US_ASCII), salt);

                        byte[] hash = new byte[0];
                        byte[] keyAndIv = new byte[0];
                        for (int i = 0; i < 3; i++) {
                            final byte[] data = MMMData.concat(hash, passAndSalt);
                            final MessageDigest md = MessageDigest.getInstance("MD5");
                            hash = md.digest(data);
                            keyAndIv = MMMData.concat(keyAndIv, hash);
                        }

                        final byte[] keyValue = Arrays.copyOfRange(keyAndIv, 0, 32);
                        final byte[] iv = Arrays.copyOfRange(keyAndIv, 32, 48);
                        final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
                        final SecretKeySpec key = new SecretKeySpec(keyValue, "AES");
                        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
                        final byte[] clear = cipher.doFinal(inBytes, 16, inBytes.length - 16);
                        final String clearText = new String(clear, ISO_8859_1);

                        html = html.replace(matcher.group(), "<a href=\"/r/MegaMegaMonitor/wiki/encrypted\">[[e["+subid+"|||"+publicText+"|||"+clearText+"]e]]</a>");

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return html;
    }

    /**
     * Move the spoil text inside of the "title" attribute to inside the link
     * tag. Then surround the spoil text with <code>[[s[</code> and <code>]s]]</code>.
     * <p/>
     * If there is no text inside of the link tag, insert "spoil".
     *
     * @param html
     * @return
     */
    private static String parseSpoilerTags(String html) {
        String spoilerText;
        String tag;
        String spoilerTeaser;
        Matcher matcher = SPOILER_PATTERN.matcher(html);

        while (matcher.find()) {
            tag = matcher.group(0);
            spoilerText = matcher.group(1);
            spoilerTeaser = matcher.group(2);
            // Remove the last </a> tag, but keep the < for parsing.
            if (tag.contains("<a href=\"/s")) {
                html = html.replace(tag, tag.substring(0, tag.length() - 4) + (spoilerTeaser.isEmpty() ? "spoiler" : "") + "&lt; [[s[ " + spoilerText + "]s]]</a>");
            }
        }

        return html;
    }

    /**
     * Parse a given list of html strings, splitting by table blocks.
     *
     * All table tags are html escaped.
     *
     * @param blocks list of html with or individual table blocks
     * @return list of html with tables split into it's entry
     */
    private static List<String> parseTableTags(List<String> blocks) {
        List<String> newBlocks = new ArrayList<>();
        for (String block : blocks) {
            if (block.contains(TABLE_START_TAG)) {
                String[] startSeperated = block.split(TABLE_START_TAG);
                newBlocks.add(startSeperated[0].trim());
                for (int i = 1; i < startSeperated.length; i++) {
                    String [] split = startSeperated[i].split(TABLE_END_TAG);
                    newBlocks.add("<table>" + split[0] + "</table>");
                    if (split.length > 1) {
                        newBlocks.add(split[1]);
                    }
                }
            } else {
                newBlocks.add(block);
            }
        }

        return newBlocks;
    }
}
