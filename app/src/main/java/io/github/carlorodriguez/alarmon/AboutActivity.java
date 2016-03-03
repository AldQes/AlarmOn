package io.github.carlorodriguez.alarmon;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.webkit.WebView;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppSettings.setTheme(getBaseContext(), AboutActivity.this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        setTitle(getString(R.string.about));

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        WebView webView = (WebView) findViewById(R.id.about_wb);

        webView.loadUrl("file:///android_asset/about.html");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
