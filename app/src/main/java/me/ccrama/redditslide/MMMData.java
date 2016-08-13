package me.ccrama.redditslide;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.util.Log;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.steadystate.css.parser.CSSOMParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.css.sac.InputSource;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSRuleList;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSStyleRule;
import org.w3c.dom.css.CSSStyleSheet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import me.ccrama.redditslide.util.LogUtil;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * Created by adrian on 8/13/16.
 */
public class MMMData {
    private static final String DATA_URL = "https://dev.megamegamonitor.com/data";

    public static SharedPreferences       dataupdates;
    public static SharedPreferences       accounts;
    public static JSONObject              data;
    public static SharedPreferences       userPrefs;
    public static HashMap<String, Bitmap> icons;
    public static boolean                 loggedin;

    public MMMData(Context context) {
        //Delete legacy file
        File legacy = context.getFileStreamPath("data.json");
        if (legacy.exists()) {
            legacy.delete();
        }
        if (accounts.contains(Authentication.name)) {
            userPrefs = context.getSharedPreferences("MMM-USER-"+Authentication.name, 0);
            File file = context.getFileStreamPath(Authentication.name+"-mmmdata.json");
            if (!file.exists()
                    || dataupdates.getLong(Authentication.name, 0) + 21600000 < System.currentTimeMillis()) {
                //Legacy storing method
                if (dataupdates.contains("data")) {
                    SharedPreferences.Editor editor = dataupdates.edit();
                    editor.remove("data");
                    editor.commit();
                }
                if (dataupdates.contains("updatetime")) {
                    SharedPreferences.Editor editor = dataupdates.edit();
                    editor.remove("updatetime");
                    editor.commit();
                }
                new DataUpdater(context).execute();
            } else {
                loadIcons(context);
                MMMData.preloadData(context);
                loggedin = true;
                Log.v(LogUtil.getTag(), "Loaded MMM data");
            }
        } else {
            loggedin = false;
            Log.v(LogUtil.getTag(), "Not logged in to MMM");
        }
    }


    public static class DataUpdater extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public DataUpdater(Context context) {
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... n) {
            String username = Authentication.name;
            String accesskey = accounts.getString(Authentication.name, "");
            updateData(mContext, username, accesskey);
            return null;
        }

        @Override
        protected void onPostExecute(Void n) {
        }
    }

    public static void updateData(Context mContext, String username, String accesskey) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(DATA_URL + "?username=" + username + "&accesskey=" + accesskey);
            c = (HttpURLConnection) u.openConnection();
            c.connect();
            int status = c.getResponseCode();
            switch (status) {
                case 200:
                case 201:
                    try {
                        FileOutputStream out =
                                mContext.openFileOutput(Authentication.name+"-mmmdata.json", Context.MODE_PRIVATE);

                        InputStream is = c.getInputStream();

                        byte[] buf = new byte[4096];
                        for (int m; 0 < (m = is.read(buf)); ) {
                            out.write(buf, 0, m);
                        }
                        out.close();

                        SharedPreferences.Editor dataeditor = MMMData.dataupdates.edit();
                        dataeditor.putLong(Authentication.name, System.currentTimeMillis());
                        dataeditor.commit();
                        Log.v(LogUtil.getTag(), "Got MMM Data");

                        MMMData.loadIcons(mContext);
                        MMMData.preloadData(mContext);

                    } catch (OutOfMemoryError e) {
                        e.printStackTrace();
                    }
                    MMMData.loggedin = true;
            }

        } catch (Exception ex) {
            Log.v(LogUtil.getTag(), "Getting MMM Data failed");
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    //disconnect error
                }
            }
        }
    }

    public static void loadIcons(Context c) {
        try {
            icons = new HashMap<>();
            JsonReader reader =
                    new JsonReader(new InputStreamReader(c.openFileInput(Authentication.name+"-mmmdata.json"), "UTF-8"));
            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("css")) {
                    InputSource source = new InputSource(new StringReader(reader.nextString()));
                    CSSOMParser parser = new CSSOMParser();
                    CSSStyleSheet sheet = parser.parseStyleSheet(source);
                    CSSRuleList ruleList = sheet.getCssRules();

                    Pattern inRegex = Pattern.compile("\\.mmm-in-sub-([-\\d]+)");
                    Pattern plusRegex = Pattern.compile("\\.mmm-icon-plus\\.mmm-icon-([-\\d]+)");
                    Pattern iconRegex = Pattern.compile("\\.mmm-icon-([-\\d]+)");

                    for (int i = 0; i < ruleList.getLength(); i++) {
                        CSSRule rule = ruleList.item(i);
                        if (rule instanceof CSSStyleRule) {
                            CSSStyleRule styleRule = (CSSStyleRule) rule;
                            String selector = styleRule.getSelectorText();
                            Matcher m = inRegex.matcher(selector);
                            if (m.find()) {
                                icons.put("in" + m.group(1), getIcon(styleRule.getStyle()));

                                Log.v(LogUtil.getTag(), "Added in" + m.group(1));
                            } else {
                                m = plusRegex.matcher(selector);
                                if (m.find()) {
                                    icons.put("plus" + m.group(1), getIcon(styleRule.getStyle()));

                                    Log.v(LogUtil.getTag(), "Added plus" + m.group(1));
                                } else {
                                    m = iconRegex.matcher(selector);
                                    if (m.find()) {
                                        icons.put(m.group(1), getIcon(styleRule.getStyle()));
                                        Log.v(LogUtil.getTag(), "Added " + m.group(1));
                                    }
                                }
                            }
                        }
                    }
                    reader.endObject();
                    break;
                } else {
                    reader.skipValue();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            Log.v(LogUtil.getTag(), "Error getting Icon data");

        }
        Log.v(LogUtil.getTag(), "Loaded " + icons.size() + " MMM icons.");
    }

    private static Pattern base64Regex = Pattern.compile("url\\(data:image/png;base64,(.+)\\)");

    private static Bitmap getIcon(CSSStyleDeclaration style) {

        String value = style.getPropertyValue("background-image");
        Matcher m = base64Regex.matcher(value);
        if (m.find()) {
            String imageString = m.group(1);
            byte[] imageBytes = Base64.decode(imageString, 0);

            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            return bitmap;
        }
        return null;
    }

    public static void preloadData(Context c) throws OutOfMemoryError {
        if(((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass() > 32 && !SettingValues.mmmLowMem){
            try {
                FileInputStream in = c.openFileInput(Authentication.name+"-mmmdata.json");
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for (int n; 0 < (n = in.read(buf)); ) {
                    out.write(buf, 0, n);
                }
                out.close();
                try {
                    data = new JSONObject(out.toString());
                } catch (OutOfMemoryError e) {
                    data = null;
                    Log.v(LogUtil.getTag(), "Couldn't preload data.");
                }

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
        else {
            Log.v(LogUtil.getTag(), "Detected small memory. Won't preload data.");
        }
    }

    public static SpannableStringBuilder addIcons(Context c, SpannableStringBuilder titleString,
            int fontsize, String author, String subreddit) {
        if (loggedin) {
            if (MMMData.data != null) {
                try {
                    ArrayList<SpannableStringBuilder> iconlist = new ArrayList<>();
                    int highest = 0;
                    int highestindex = -1;
                    JSONArray subreddits = MMMData.data.getJSONArray("subs");
                    for (int i = 0; i < subreddits.length(); i++) {
                        if (MMMData.userPrefs.getBoolean(subreddits.getJSONObject(i).getString("id"), true)) {
                            JSONArray users = subreddits.getJSONObject(i).getJSONArray("users");
                            for (int j = 0; j < users.length(); j++) {
                                if (users.getJSONArray(j).getString(0).equals(author)) {
                                    SpannableStringBuilder icon = new SpannableStringBuilder(" "
                                            + subreddits.getJSONObject(i).getString("display_name")
                                            + " ");
                                    Bitmap image = null;
                                    if (users.getJSONArray(j).getString(1).equals("+")
                                            && MMMData.icons.containsKey(
                                            "plus" + subreddits.getJSONObject(i).getInt("id"))) {
                                        image = MMMData.icons.get(
                                                "plus" + subreddits.getJSONObject(i).getInt("id"));
                                    } else if (subreddits.getJSONObject(i)
                                            .getString("display_name")
                                            .equals(subreddit) && MMMData.icons.containsKey(
                                            "in" + subreddits.getJSONObject(i).getInt("id"))) {
                                        image = MMMData.icons.get(
                                                "in" + subreddits.getJSONObject(i).getInt("id"));
                                    } else if (MMMData.icons.containsKey(
                                            "" + subreddits.getJSONObject(i).getInt("id"))) {
                                        image = MMMData.icons.get(
                                                "" + subreddits.getJSONObject(i).getInt("id"));
                                    }
                                    if (image != null) {
                                        float aspectRatio = (float) (1.00 * image.getWidth() / image.getHeight());
                                        image = Bitmap.createScaledBitmap(image,
                                                (int) Math.ceil(fontsize * aspectRatio),
                                                (int) Math.ceil(fontsize), true);
                                        icon.setSpan(new ImageSpan(c, image), 0, icon.length(), 0);
                                        if (!subreddits.getJSONObject(i).isNull("chain_number")) {
                                            int chain_number = subreddits.getJSONObject(i).getInt("chain_number");

                                            if (chain_number > highest) {
                                                if (highestindex != -1) {
                                                    iconlist.remove(highestindex);
                                                }
                                                highestindex = iconlist.size();
                                                highest = chain_number;
                                                iconlist.add(icon);
                                            }
                                        } else {
                                            iconlist.add(icon);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (SpannableStringBuilder icon : iconlist) {
                        titleString.append(icon);
                        titleString.append(" ");
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
                    while (reader.hasNext()) {
                        if (reader.nextName().equals("subs")) {
                            int highestChain = -1;
                            int highestIndex = -1;
                            ArrayList<SpannableStringBuilder> iconlist = new ArrayList<>();
                            reader.beginArray();
                            while (reader.hasNext()) {
                                reader.beginObject();

                                int id = 0;
                                int chain_number = -1;
                                String display_name = "";
                                boolean isIn = false;
                                boolean isPlus = false;

                                while (reader.hasNext()) {
                                    String name = reader.nextName();
                                    if (name.equals("users")) {
                                        reader.beginArray();
                                        while (reader.hasNext()) {
                                            reader.beginArray();
                                            if (reader.nextString().equals(author)) {
                                                isIn = true;
                                                if (reader.nextString().equals("+")) {
                                                    isPlus = true;
                                                }
                                            }
                                            else {
                                                reader.skipValue();
                                            }

                                            reader.endArray();
                                        }
                                        reader.endArray();
                                    } else if (name.equals("chain_number")) {
                                        if (reader.peek() != JsonToken.NULL) {
                                            chain_number = reader.nextInt();
                                        }
                                        else {
                                            reader.skipValue();
                                        }
                                    } else if (name.equals("id")) {
                                        id = reader.nextInt();
                                    } else if (name.equals("display_name")) {
                                        display_name = reader.nextString();
                                    }
                                    else {
                                        reader.skipValue();
                                    }
                                }
                                if (isIn) {
                                    SpannableStringBuilder icon =
                                            new SpannableStringBuilder(" " + display_name + " ");
                                    Bitmap image = null;
                                    if (isPlus && MMMData.icons.containsKey("plus" + id)) {
                                        image = MMMData.icons.get("plus" + id);
                                    } else if (display_name.equals(subreddit)
                                            && MMMData.icons.containsKey("in" + id)) {
                                        image = MMMData.icons.get("in" + id);
                                    } else if (MMMData.icons.containsKey("" + id)) {
                                        image = MMMData.icons.get("" + id);
                                    }
                                    if (image != null) {
                                        float aspectRatio = (float) (1.00 * image.getWidth()
                                                / image.getHeight());
                                        image = Bitmap.createScaledBitmap(image,
                                                (int) Math.ceil(fontsize * aspectRatio),
                                                (int) Math.ceil(fontsize), true);
                                        icon.setSpan(new ImageSpan(c, image), 0, icon.length(), 0);
                                        if (chain_number != -1) {

                                            if (chain_number > highestChain) {
                                                if (highestIndex != -1) {
                                                    iconlist.remove(highestIndex);
                                                }
                                                highestIndex = iconlist.size();
                                                highestChain = chain_number;
                                                iconlist.add(icon);
                                            }
                                        } else {
                                            iconlist.add(icon);
                                        }
                                    }
                                }
                                reader.endObject();


                            }
                            reader.endArray();
                            for (SpannableStringBuilder icon : iconlist) {
                                titleString.append(icon);
                                titleString.append(" ");
                            }
                        }
                        else {
                            reader.skipValue();
                        }
                    }
                    reader.endObject();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return titleString;
    }

    public  static String[][] getSubreddits(Context context) {
        if (loggedin) {
            if (data != null) {
                try {
                    JSONArray subs = data.getJSONArray("subs");
                    String[][] return_ = new String[2][subs.length()];
                    for (int i = 0; i < subs.length(); i++)
                    {
                        return_[0][i] = subs.getJSONObject(i).getString("display_name");
                        return_[1][i] = subs.getJSONObject(i).getString("id");
                    }
                    return return_;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    ArrayList<String>[] subs = new ArrayList[2];
                    subs[0] = new ArrayList<String>();
                    subs[1] = new ArrayList<String>();
                    JsonReader reader = new JsonReader(
                            new InputStreamReader(context.openFileInput(Authentication.name+"-mmmdata.json"), "UTF-8"));
                    reader.beginObject();
                    while (reader.hasNext()) {
                        if (reader.nextName().equals("subs")) {
                            reader.beginArray();
                            while (reader.hasNext()) {
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    if (reader.nextName().equals("display_name")) {
                                        subs[0].add(reader.nextString());
                                    }
                                    else if (reader.nextName().equals("id")) {
                                        subs[1].add(reader.nextString());
                                    }
                                    else {
                                        reader.skipValue();
                                    }
                                }
                                reader.endObject();
                            }
                            reader.endArray();
                        }
                        else {
                            reader.skipValue();
                        }
                    }
                    reader.close();
                    return new String[][]{subs[0].toArray(new String[subs[0].size()]), subs[1].toArray(new String[subs[1].size()])};
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }

        }
        return new String[2][0];
    }

    public static Passwordinfo getPassword(Context context, String subreddit) {
        if (loggedin) {
            if (data != null) {
                try {
                    JSONArray subs = data.getJSONArray("subs");
                    for (int i = 0; i < subs.length(); i++)
                    {
                        if (subs.getJSONObject(i).getString("display_name").equals(subreddit)) {
                            JSONArray keys = subs.getJSONObject(i).getJSONArray("cryptokeys");
                            int highest = -2;
                            String key = null;
                            for (int j = 0; j < keys.length(); j++) {
                                if (keys.getJSONArray(j).getInt(0) > highest) {
                                    highest = keys.getJSONArray(j).getInt(0);
                                    key = keys.getJSONArray(j).getString(1);
                                }
                            }
                            return new Passwordinfo(key, highest);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            else {
                try {
                    boolean hasFound = false;
                    String key = null;
                    JsonReader reader = new JsonReader(
                            new InputStreamReader(context.openFileInput(Authentication.name+"-mmmdata.json"), "UTF-8"));
                    reader.beginObject();
                    while (reader.hasNext()) {
                        if (reader.nextName().equals("subs")) {
                            reader.beginArray();
                            while (reader.hasNext()) {
                                reader.beginObject();
                                int highest = -2;
                                while (reader.hasNext()) {
                                    String name = reader.nextName();
                                    if (name.equals("display_name")) {
                                        if (reader.nextString().equals(subreddit)) {
                                            hasFound = true;
                                        }
                                    } else if(name.equals("cryptokeys")) {
                                        highest = -2;
                                        reader.beginArray();
                                        while (reader.hasNext()) {
                                            reader.beginArray();
                                            int current = reader.nextInt();
                                            if (current > highest) {
                                                highest = current;
                                                key = reader.nextString();
                                            } else {
                                                reader.skipValue();
                                            }
                                            reader.endArray();
                                        }
                                        reader.endArray();
                                    } else {
                                        reader.skipValue();
                                    }
                                }
                                if (hasFound) {
                                    reader.close();
                                    return new Passwordinfo(key, highest);
                                }
                                reader.endObject();
                            }
                            reader.endArray();
                        }
                        else {
                            reader.skipValue();
                        }
                    }
                    reader.close();
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }

        }
        return null;
    }

    public static class Passwordinfo {
        public String password;
        public int id;

        public Passwordinfo(String password, int id) {
            this.password = password;
            this.id = id;
        }
    }

    public static String encrypt(String plaintext, String password) {
        try {
            final byte[] inBytes = plaintext.getBytes();

            final byte[] salt = new byte[8];
            new SecureRandom().nextBytes(salt);

            final byte[] passAndSalt = concat(password.getBytes(US_ASCII), salt);

            byte[] hash = new byte[0];
            byte[] keyAndIv = new byte[0];
            for (int i = 0; i < 3; i++) {
                final byte[] data = concat(hash, passAndSalt);
                final MessageDigest md = MessageDigest.getInstance("MD5");
                hash = md.digest(data);
                keyAndIv = concat(keyAndIv, hash);
            }

            final byte[] keyValue = Arrays.copyOfRange(keyAndIv, 0, 32);
            final byte[] iv = Arrays.copyOfRange(keyAndIv, 32, 48);
            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            final SecretKeySpec key = new SecretKeySpec(keyValue, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            final byte[] encrypted = cipher.doFinal(inBytes);
            final String cryptoText = Base64.encodeToString(concat(concat("Salted__".getBytes(US_ASCII), salt), encrypted), Base64.NO_WRAP);

            return cryptoText;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] concat(final byte[] a, final byte[] b) {
        final byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}