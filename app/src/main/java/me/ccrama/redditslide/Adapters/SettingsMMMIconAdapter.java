package me.ccrama.redditslide.Adapters;

import android.app.Activity;
import android.graphics.Bitmap;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import me.ccrama.redditslide.MMMData;
import me.ccrama.redditslide.R;

/**
 * Created by adrian on 2/9/17.
 */

public class SettingsMMMIconAdapter extends RecyclerView.Adapter<SettingsMMMIconAdapter.ViewHolder> {
    private final String[][] subs;

    private Activity context;

    public SettingsMMMIconAdapter(Activity context, String[][] subs) {
        this.subs = subs;
        this.context = context;
    }

    @Override
    public SettingsMMMIconAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.iconforiconlist, parent, false);
        return new SettingsMMMIconAdapter.ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(SettingsMMMIconAdapter.ViewHolder holder, int position) {
        final String sub = subs[0][position];
        final String id = subs[1][position];
        final View convertView = holder.itemView;
        final TextView t = ((TextView) convertView.findViewById(R.id.name));
        t.setText(sub);

        Bitmap icon = MMMData.icons.get(id);
        ((ImageView) convertView.findViewById(R.id.icon)).setImageBitmap(icon);
        boolean enabled = MMMData.userPrefs.getBoolean(id, true);
        ((SwitchCompat) convertView.findViewById(R.id.enabled)).setChecked(enabled);
        ((SwitchCompat) convertView.findViewById(R.id.enabled)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                MMMData.userPrefs.edit()
                        .putBoolean(id, isChecked)
                        .apply();
            }
        });
    }

    @Override
    public int getItemCount() {
        return subs[0].length;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View itemView) {
            super(itemView);
        }
    }


}
