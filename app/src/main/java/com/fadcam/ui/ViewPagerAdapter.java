package com.fadcam.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                return new RecordsFragment();
            case 2:
                return new SettingsFragment();
            case 3:
            default:
                return new HomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3; // Number of fragments
    }
}