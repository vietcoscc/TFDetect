package org.tensorflow.demo.env;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.tensorflow.demo.R;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by viet on 01/02/2018.
 */

public class DetectedObjectAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private ArrayList<DetectedObject> arrObject = new ArrayList<>();

    public DetectedObjectAdapter(ArrayList<DetectedObject> arrObject) {
        this.arrObject = arrObject;
    }

    public DetectedObjectAdapter() {

    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detected_object_recycler_view, parent, false);
        return new DetectedObjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (arrObject.isEmpty()) {
            return;
        }
        ((DetectedObjectViewHolder) holder).bindView(arrObject.get(position));
    }

    @Override
    public int getItemCount() {
        return arrObject.size();
    }

    class DetectedObjectViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.ivImage)
        ImageView ivImage;
        @BindView(R.id.tvInfo)
        TextView tvInfo;

        public DetectedObjectViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        public void bindView(DetectedObject detectedObject) {
            ivImage.setImageBitmap(detectedObject.getBitmap());
            tvInfo.setText(detectedObject.getRecognition().getTitle().trim());
        }
    }

    public void addItem(DetectedObject detectedObject) {
        if (arrObject.size() > 6) {
            removeItem(arrObject.size() - 1);
        }
        arrObject.add(0, detectedObject);
        notifyItemInserted(0);

    }

    public void removeItem(int position) {
        if (position < 0 || position > arrObject.size() - 1) {
            return;
        }
        arrObject.remove(position);
        notifyItemRemoved(position);
    }

    public void clear() {
        arrObject.clear();
        notifyDataSetChanged();
    }
}
