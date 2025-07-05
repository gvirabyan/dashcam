package com.fadcam.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.fadcam.R;
import com.fadcam.model.TrashItem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.bumptech.glide.Glide;
import android.net.Uri;
import java.io.File;
import com.fadcam.utils.TrashManager;
import android.util.Log;
import android.widget.Toast;
import com.fadcam.SharedPreferencesManager;
import java.util.concurrent.TimeUnit;
import com.fadcam.Constants;

public class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.TrashViewHolder> {

    private final Context context;
    private final List<TrashItem> trashItems;
    private final List<TrashItem> selectedItems = new ArrayList<>();
    private final OnTrashItemInteractionListener interactionListener;
    private final OnTrashItemLongClickListener longClickListener; // Optional
    private final SharedPreferencesManager sharedPreferencesManager;
    private boolean isSnowVeilTheme = false;

    public interface OnTrashItemInteractionListener {
        void onItemCheckChanged(TrashItem item, boolean isChecked);
        void onItemSelectedStateChanged(boolean anySelected);
        void onRestoreStarted(int itemCount);
        void onRestoreFinished(boolean success, String message);
        void onPlayVideoRequested(TrashItem item);
    }

    public interface OnTrashItemLongClickListener {
        void onItemLongClicked(TrashItem item);
    }

    public TrashAdapter(Context context, List<TrashItem> trashItems,
                          OnTrashItemInteractionListener interactionListener,
                          OnTrashItemLongClickListener longClickListener) {
        this.context = context;
        this.trashItems = trashItems;
        this.interactionListener = interactionListener;
        this.longClickListener = longClickListener;
        
        // Get current theme for theming
        sharedPreferencesManager = SharedPreferencesManager.getInstance(context);
        String currentTheme = sharedPreferencesManager.sharedPreferences.getString(com.fadcam.Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME);
        this.isSnowVeilTheme = "Snow Veil".equals(currentTheme);
    }

    @NonNull
    @Override
    public TrashViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_trash, parent, false);
        return new TrashViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrashViewHolder holder, int position) {
        TrashItem item = trashItems.get(position);
        holder.bind(item);
        
        // Apply Snow Veil theme if needed (apply after normal binding)
        if (isSnowVeilTheme && holder.itemView instanceof androidx.cardview.widget.CardView) {
            // Set white background for card
            ((androidx.cardview.widget.CardView) holder.itemView).setCardBackgroundColor(android.graphics.Color.WHITE);
            
            // Set black text for better contrast
            if (holder.tvOriginalName != null) {
                holder.tvOriginalName.setTextColor(android.graphics.Color.BLACK);
            }
            if (holder.tvDateTrashed != null) {
                holder.tvDateTrashed.setTextColor(android.graphics.Color.BLACK);
            }
            if (holder.tvOriginalLocation != null) {
                holder.tvOriginalLocation.setTextColor(android.graphics.Color.BLACK);
            }
            // Keep the remaining time text color dynamic based on urgency
        }
    }

    @Override
    public int getItemCount() {
        return trashItems.size();
    }

    public List<TrashItem> getSelectedItems() {
        return new ArrayList<>(selectedItems); // Return a copy
    }

    public int getSelectedItemsCount() {
        return selectedItems.size();
    }

    /**
     * Selects all items in the list
     */
    public void selectAll() {
        selectedItems.clear();
        selectedItems.addAll(trashItems);
        notifyDataSetChanged();
        if (interactionListener != null) {
            interactionListener.onItemSelectedStateChanged(true);
        }
    }

    /**
     * Clears all selected items and updates the UI accordingly
     */
    public void clearSelections() {
        if (!selectedItems.isEmpty()) {
            selectedItems.clear();
            notifyDataSetChanged();
            if (interactionListener != null) {
                interactionListener.onItemSelectedStateChanged(false);
            }
        }
    }

    /**
     * Checks if all items are currently selected
     * @return true if all items are selected, false otherwise
     */
    public boolean isAllSelected() {
        return !trashItems.isEmpty() && selectedItems.size() == trashItems.size();
    }

    class TrashViewHolder extends RecyclerView.ViewHolder {
        TextView tvOriginalName;
        TextView tvDateTrashed;
        TextView tvOriginalLocation;
        CheckBox checkBoxSelected;
        ImageView imageViewThumbnail;
        TextView tvRemainingTime;

        TrashViewHolder(@NonNull View itemView) {
            super(itemView);
            imageViewThumbnail = itemView.findViewById(R.id.image_view_trash_thumbnail);
            tvOriginalName = itemView.findViewById(R.id.tv_trash_item_original_name);
            tvDateTrashed = itemView.findViewById(R.id.tv_trash_item_date_trashed);
            tvOriginalLocation = itemView.findViewById(R.id.tv_trash_item_original_location);
            checkBoxSelected = itemView.findViewById(R.id.checkbox_trash_item_selected);
            tvRemainingTime = itemView.findViewById(R.id.tv_trash_item_remaining_time);
        }

        void bind(final TrashItem item) {
            tvOriginalName.setText(item.getOriginalDisplayName());
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            tvDateTrashed.setText("Trashed: " + sdf.format(new Date(item.getDateTrashed())));
            tvOriginalLocation.setText("Original: " + (item.isFromSaf() ? "SAF Storage" : "Internal Storage"));

            if (tvRemainingTime != null && sharedPreferencesManager != null && context != null) {
                int autoDeleteMinutes = sharedPreferencesManager.getTrashAutoDeleteMinutes();
                if (autoDeleteMinutes == SharedPreferencesManager.TRASH_AUTO_DELETE_NEVER) {
                    tvRemainingTime.setText(context.getString(R.string.trash_auto_delete_info_manual));
                    tvRemainingTime.setTextColor(context.getResources().getColor(R.color.gray_text_very_light));
                } else {
                    long timeSinceTrashedMillis = System.currentTimeMillis() - item.getDateTrashed();
                    long autoDeleteTotalMillis = TimeUnit.MINUTES.toMillis(autoDeleteMinutes);
                    long remainingMillis = autoDeleteTotalMillis - timeSinceTrashedMillis;

                    if (remainingMillis <= 0) {
                        tvRemainingTime.setText(context.getString(R.string.trash_item_remaining_soon));
                        tvRemainingTime.setTextColor(context.getResources().getColor(R.color.colorError));
                    } else {
                        long remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMillis);
                        if (remainingDays > 0) {
                            tvRemainingTime.setText(context.getResources().getQuantityString(R.plurals.trash_item_remaining_days, (int)remainingDays, (int)remainingDays));
                        } else {
                            long remainingHours = TimeUnit.MILLISECONDS.toHours(remainingMillis);
                            if (remainingHours > 0) {
                                tvRemainingTime.setText(context.getResources().getQuantityString(R.plurals.trash_item_remaining_hours, (int)remainingHours, (int)remainingHours));
                            } else {
                                tvRemainingTime.setText(context.getString(R.string.trash_item_remaining_soon));
                            }
                        }
                        
                        if (remainingDays == 0 && TimeUnit.MILLISECONDS.toHours(remainingMillis) < 1) {
                            tvRemainingTime.setTextColor(context.getResources().getColor(R.color.colorError));
                        } else if (remainingDays == 0 && TimeUnit.MILLISECONDS.toHours(remainingMillis) < 12) {
                            tvRemainingTime.setTextColor(context.getResources().getColor(R.color.colorWarning));
                        } else if (remainingDays < 3) {
                            tvRemainingTime.setTextColor(context.getResources().getColor(R.color.colorWarning));
                        } else {
                            tvRemainingTime.setTextColor(context.getResources().getColor(R.color.gray_text_light));
                        }
                    }
                }
            } else {
                if(tvRemainingTime != null) tvRemainingTime.setVisibility(View.GONE);
            }

            if (imageViewThumbnail != null && context != null) {
                File trashDirectory = TrashManager.getTrashDirectory(context);
                if (trashDirectory != null && item.getTrashFileName() != null) {
                    File trashedVideoFile = new File(trashDirectory, item.getTrashFileName());
                    if (trashedVideoFile.exists()) {
                        Uri videoUri = Uri.fromFile(trashedVideoFile);
                        Glide.with(context)
                            .load(videoUri)
                            .placeholder(R.drawable.ic_video_placeholder)
                            .error(R.drawable.ic_error)
                            .centerCrop()
                            .into(imageViewThumbnail);
                    } else {
                        Log.w("TrashAdapter", "Trashed video file does not exist: " + trashedVideoFile.getAbsolutePath());
                        Glide.with(context).load(R.drawable.ic_error).into(imageViewThumbnail); // Show error icon
                    }
                } else {
                    Log.e("TrashAdapter", "Trash directory or trash file name is null.");
                    Glide.with(context).load(R.drawable.ic_error).into(imageViewThumbnail); // Show error icon
                }
            }

            // Set the checkbox state without triggering onCheckedChangeListener
            checkBoxSelected.setOnCheckedChangeListener(null);
            checkBoxSelected.setChecked(selectedItems.contains(item));
            
            // Set up click listeners
            itemView.setOnClickListener(v -> {
                if (selectedItems.isEmpty()) {
                    if (interactionListener != null) {
                        File trashDirectory = TrashManager.getTrashDirectory(context);
                        if (trashDirectory != null && item.getTrashFileName() != null) {
                            File trashedVideoFile = new File(trashDirectory, item.getTrashFileName());
                            if (trashedVideoFile.exists()) {
                                interactionListener.onPlayVideoRequested(item); // item itself is fine, TrashFragment will reconstruct path
                            } else {
                                Toast.makeText(context, "Video file not found in trash.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(context, "Cannot locate video file.", Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    toggleSelection(item);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onItemLongClicked(item);
                    return true;
                }
                toggleSelection(item);
                return true;
            });

            // Re-add the listener after setting the initial state
            checkBoxSelected.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && !selectedItems.contains(item)) {
                    selectedItems.add(item);
                    if (interactionListener != null) {
                        interactionListener.onItemCheckChanged(item, true);
                    }
                } else if (!isChecked && selectedItems.contains(item)) {
                    selectedItems.remove(item);
                    if (interactionListener != null) {
                        interactionListener.onItemCheckChanged(item, false);
                    }
                }
            });
        }
    }
    
    /**
     * Toggle selection state of an item
     */
    private void toggleSelection(TrashItem item) {
        boolean newState = !selectedItems.contains(item);
        if (newState) {
            selectedItems.add(item);
        } else {
            selectedItems.remove(item);
        }
        
        // Find the position of the item and notify the adapter
        int position = trashItems.indexOf(item);
        if (position != -1) {
            notifyItemChanged(position);
        }
        
        if (interactionListener != null) {
            interactionListener.onItemCheckChanged(item, newState);
        }
    }
} 