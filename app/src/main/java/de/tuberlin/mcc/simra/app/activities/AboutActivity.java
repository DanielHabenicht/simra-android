package de.tuberlin.mcc.simra.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import de.tuberlin.mcc.simra.app.R;
import de.tuberlin.mcc.simra.app.util.BaseActivity;

public class AboutActivity extends BaseActivity {

    ImageButton backBtn;
    TextView toolbarTxt;
    ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_simra);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setTitle("");
        toolbar.setSubtitle("");
        toolbarTxt = findViewById(R.id.toolbar_title);
        toolbarTxt.setText(R.string.title_activity_about_simra);

        backBtn = findViewById(R.id.back_button);
        backBtn.setOnClickListener(v -> finish());

        listView = findViewById(R.id.listView);

        String[] items = getResources().getStringArray(R.array.aboutSimraItems);
        listView.setAdapter(new ArrayAdapter<>(AboutActivity.this,
                android.R.layout.simple_list_item_1, items));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = null;
            switch (position) {
                case 0:
                    intent = new Intent(AboutActivity.this, WebActivity.class);
                    intent.putExtra("URL", getString(R.string.simRaPage));
                    break;
                case 1:
                    intent = new Intent(AboutActivity.this, WebActivity.class);
                    intent.putExtra("URL", getString(R.string.privacyLink));

                    break;
                case 2:
                    intent = new Intent(AboutActivity.this, LicenseActivity.class);
                    break;
                case 3:
                    intent = new Intent(AboutActivity.this, CreditsActivity.class);
                    break;
                default:
                    Toast.makeText(AboutActivity.this, R.string.notReady, Toast.LENGTH_SHORT).show();
            }
            if (intent != null) {
                startActivity(intent);
            }


        });

    }
}