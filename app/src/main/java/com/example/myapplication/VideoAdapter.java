package com.example.myapplication;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
    private Context context;
    private List<String> videoTitles;
    private List<String> videoImageUrls;
    private List<String> videoUrls;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(String videoUrl);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public VideoAdapter(Context context, List<String> videoTitles, List<String> videoImageUrls, List<String> videoUrls) {
        this.context = context;
        this.videoTitles = videoTitles;
        this.videoImageUrls = videoImageUrls;
        this.videoUrls = videoUrls;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.video_item, parent, false);
        return new VideoViewHolder(view);
    }
    public void clearData() {
        videoTitles.clear();
        videoImageUrls.clear();
        videoUrls.clear();
        notifyDataSetChanged();
    }
    public void addData(List<String> newTitles, List<String> newImageUrls, List<String> newUrls) {
        int startPosition = videoTitles.size(); // Obtener la posición de inicio para agregar nuevos elementos
        Log.d("VideoAdapter", "Addedeeee " + newUrls.size() + " new videos");
        videoTitles.addAll(newTitles);
        videoImageUrls.addAll(newImageUrls);
        videoUrls.addAll(newUrls);

        notifyItemRangeInserted(startPosition, newUrls.size()); // Notificar al adaptador sobre la inserción de nuevos elementos
    }
    public void setData(List<String> videoTitles, List<String> videoImageUrls, List<String> videoIds) {
        this.videoTitles.clear();
        this.videoImageUrls.clear();
        this.videoUrls.clear();
        addData(videoTitles, videoImageUrls, videoIds);
    }


    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        holder.bind(videoTitles.get(position), videoImageUrls.get(position), videoUrls.get(position));
    }

    @Override
    public int getItemCount() {
        return videoUrls.size();
    }

    public void updateData(List<String> newTitles, List<String> newImageUrls, List<String> newUrls) {
        videoTitles.clear();
        videoTitles.addAll(newTitles);
        videoImageUrls.clear();
        videoImageUrls.addAll(newImageUrls);
        videoUrls.clear();
        videoUrls.addAll(newUrls);
        notifyDataSetChanged();
    }

    // Métodos para obtener las listas actuales
    public List<String> getTitles() {
        return videoTitles;
    }

    public List<String> getVideoImageUrls() {
        return videoImageUrls;
    }

    public List<String> getVideoIds() {
        return videoUrls;
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageView image;

        VideoViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.videoTitle);
            image = itemView.findViewById(R.id.videoImage);
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(videoUrls.get(position));
                }
            });
        }

        void bind(String videoTitle, String imageUrl, String videoUrl) {
            title.setText(videoTitle);
            Glide.with(context).load(imageUrl).into(image);
        }
    }
}

