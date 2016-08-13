package me.ccrama.redditslide.Activities;

import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import me.ccrama.redditslide.Adapters.SettingsMMMIconAdapter;
import me.ccrama.redditslide.MMMData;
import me.ccrama.redditslide.R;

/**
 * Created by adrian on 2/9/17.
 */

public class SettingsMMMIcons extends BaseActivityAnim {
    public SettingsMMMIconAdapter mSettingsMMMIconAdapter;

    private RecyclerView recycler;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_icons_mmm);
        setupAppBar(R.id.toolbar, R.string.settings_title_icons_mmm, true, true);

        if (!MMMData.loggedin) {
            findViewById(R.id.refresh_data).setVisibility(View.GONE);
            findViewById(R.id.logout_mmm).setVisibility(View.GONE);
            findViewById(R.id.hide_icons_mmm).setVisibility(View.GONE);

        } else {
            recycler = ((RecyclerView) findViewById(R.id.iconlist));
            recycler.setLayoutManager(new LinearLayoutManager(this));

            String[][] icons = MMMData.getSubreddits(this);

            mSettingsMMMIconAdapter =  new SettingsMMMIconAdapter(this, icons);
            recycler.setAdapter(mSettingsMMMIconAdapter);
        }
    }
}
