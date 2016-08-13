package me.ccrama.redditslide.Activities;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.UrlQuerySanitizer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.afollestad.materialdialogs.MaterialDialog;

import me.ccrama.redditslide.Authentication;
import me.ccrama.redditslide.CaseInsensitiveArrayList;
import me.ccrama.redditslide.MMMData;
import me.ccrama.redditslide.R;
import me.ccrama.redditslide.util.LogUtil;


/**
 * Created by ccrama on 5/27/2015.
 */
public class MMMLogin extends BaseActivityAnim {
    private static final String CLIENT_ID = "YeKY_A2Sj0ddXA";
    private static final String LOGIN_URL = "https://dev.megamegamonitor.com/login";
    private static final String DATA_URL  = "https://dev.megamegamonitor.com/data";
    Dialog            d;
    CaseInsensitiveArrayList subNames;

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        applyColorTheme("");
        setContentView(R.layout.activity_mmm_login);
        setupAppBar(R.id.toolbar, R.string.title_mmm_login, true, true);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookie();
        final WebView webView = (WebView) findViewById(R.id.web);

        webView.loadUrl(LOGIN_URL);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
//                activity.setProgress(newProgress * 1000);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url.contains("setaccessuser") && url.contains("setaccesskey=")) {
                    Log.v(LogUtil.getTag(), "WebView URL: " + url);
                    // Authentication code received, prevent HTTP call from being made.
                    webView.stopLoading();
                    new UserChallengeTask(MMMLogin.this).execute(url);
                    webView.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private final class UserChallengeTask extends AsyncTask<String, Void, Void> {
        private Context        mContext;
        private MaterialDialog mMaterialDialog;

        public UserChallengeTask(Context context) {
            mContext = context;
            Log.v(LogUtil.getTag(), "UserChallengeTask()");
        }

        @Override
        protected void onPreExecute() {
            //Show a dialog to indicate progress
            MaterialDialog.Builder builder = new MaterialDialog.Builder(MMMLogin.this).title(
                    R.string.mmmlogin_authenticating)
                    .progress(true, 0)
                    .content(R.string.misc_please_wait)
                    .cancelable(false);
            mMaterialDialog = builder.build();
            mMaterialDialog.show();
        }

        @Override
        protected Void doInBackground(String... params) {
            String url = params[0];
            UrlQuerySanitizer sanitizer = new UrlQuerySanitizer();
            sanitizer.setAllowUnregisteredParamaters(true);
            sanitizer.parseUrl(url);
            String username = sanitizer.getValue("setaccessuser");
            String accesskey = sanitizer.getValue("setaccesskey");
            Log.v(LogUtil.getTag(), "Username: " + username);
            Log.v(LogUtil.getTag(), "Accesskey: " + accesskey);

            SharedPreferences.Editor editor = MMMData.accounts.edit();
            editor.putString(username, accesskey);
            editor.commit();

            MMMData.userPrefs = mContext.getSharedPreferences("MMM-USER-"+ Authentication.name, 0);
            MMMData.updateData(mContext, username, accesskey);
            return null;
        }


        @Override
        protected void onPostExecute(Void n) {
            //Dismiss old progress dialog
            mMaterialDialog.dismiss();
            SettingsTheme.changed = true;
            finish();
        }
    }
}

