package me.ccrama.redditslide.Activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import java.text.SimpleDateFormat;

import me.ccrama.redditslide.Authentication;
import me.ccrama.redditslide.MMMData;
import me.ccrama.redditslide.R;
import me.ccrama.redditslide.SettingValues;
import me.ccrama.redditslide.util.OnSingleClickListener;


/**
 * Created by ccrama on 3/5/2015.
 */
public class SettingsMMM extends BaseActivityAnim {

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_mmm);
        setupAppBar(R.id.toolbar, R.string.settings_title_mmm, true, true);

        {
            SwitchCompat single = (SwitchCompat) findViewById(R.id.fore_low_mem_mode);

            single.setChecked(SettingValues.mmmLowMem);
            single.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SettingValues.mmmLowMem = isChecked;
                    SettingValues.prefs.edit()
                            .putBoolean(SettingValues.MMM_FORCE_LOW_MEM, isChecked)
                            .apply();
                    MMMData.data = null;
                }
            });
        }

        if (!MMMData.loggedin) {
            findViewById(R.id.refresh_data).setVisibility(View.GONE);
            findViewById(R.id.logout_mmm).setVisibility(View.GONE);
            findViewById(R.id.hide_icons_mmm).setVisibility(View.GONE);

        } else {
            findViewById(R.id.not_loggedin_mmm).setVisibility(View.GONE);
            ((TextView) findViewById(R.id.last_updated)).setText(SettingsMMM.this.getResources()
                    .getString(R.string.last_refreshed_mmm,
                            new SimpleDateFormat("HH:mm MM.dd.yyyy").format(
                                    MMMData.dataupdates.getLong(Authentication.name, 0))));
            findViewById(R.id.refresh_data).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new DataUpdater(SettingsMMM.this).execute();
                }
            });

            findViewById(R.id.logout_mmm).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SettingsTheme.changed = true;
                    SharedPreferences.Editor editor = MMMData.accounts.edit();
                    editor.remove(Authentication.name);
                    editor.commit();
                    deleteFile(Authentication.name+"-mmmdata.json");
                    MMMData.data = null;
                    MMMData.loggedin = false;

                    findViewById(R.id.refresh_data).setVisibility(View.GONE);
                    findViewById(R.id.logout_mmm).setVisibility(View.GONE);
                }
            });

            findViewById(R.id.hide_icons_mmm).setOnClickListener(new OnSingleClickListener() {
                @Override
                public void onSingleClick(View v) {
                    Intent inte = new Intent(SettingsMMM.this, SettingsMMMIcons.class);
                    SettingsMMM.this.startActivity(inte);
                }
            });
        }
    }

    public static class DataUpdater extends AsyncTask<Void, Void, Void> {
        private Context        mContext;
        private MaterialDialog mMaterialDialog;

        public DataUpdater(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            MaterialDialog.Builder builder =
                    new MaterialDialog.Builder(mContext).title(R.string.downloading_mmm_data)
                            .progress(true, 0)
                            .content(R.string.misc_please_wait)
                            .cancelable(false);
            mMaterialDialog = builder.build();
            mMaterialDialog.show();
        }

        @Override
        protected Void doInBackground(Void... n) {
            String username = Authentication.name;
            String accesskey = MMMData.accounts.getString(Authentication.name, "");
            MMMData.updateData(mContext, username, accesskey);
            return null;
        }

        @Override
        protected void onPostExecute(Void n) {
            mMaterialDialog.dismiss();
            ((TextView) ((Activity) mContext).findViewById(R.id.last_updated)).setText(
                    mContext.getResources()
                            .getString(R.string.last_refreshed_mmm,
                                    new SimpleDateFormat("HH:mm MM.dd.yyyy").format(
                                            MMMData.dataupdates.getLong(Authentication.name, 0))));
            Settings.changed = true;
        }
    }
}