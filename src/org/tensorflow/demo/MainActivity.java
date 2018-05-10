package org.tensorflow.demo;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by viet on 22/01/2018.
 */

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TYPE_DETECTION = "type";

    @BindView(R.id.btnTensorflow)
    Button btnTensorflow;
    @BindView(R.id.btnTensorflow2)
    Button btnTensorflow2;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        btnTensorflow.setOnClickListener(this);
        btnTensorflow2.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btnTensorflow) {
            Intent intent = new Intent(this, DetectorActivity.class);
            intent.putExtra(TYPE_DETECTION, true);
            startActivity(intent);
        } else if (v.getId() == R.id.btnTensorflow2) {
            Intent intent = new Intent(this, DetectorActivity.class);
            intent.putExtra(TYPE_DETECTION, false);
            startActivity(intent);
        }
    }

}
