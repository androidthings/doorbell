package com.google.samples.mysample;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class DoorbellEntryViewHolder extends RecyclerView.ViewHolder {
    TextView textView1;
    TextView textView2;
    ImageView imageView1;
    public DoorbellEntryViewHolder(View view) {
        super(view);
        textView1 = (TextView) view.findViewById(R.id.textView1);
        textView2 = (TextView) view.findViewById(R.id.textView2);
        imageView1 = (ImageView) view.findViewById(R.id.imageView1);
    }
}