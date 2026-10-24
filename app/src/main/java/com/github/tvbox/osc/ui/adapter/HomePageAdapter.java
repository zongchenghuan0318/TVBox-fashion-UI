package com.github.tvbox.osc.ui.adapter;

import android.view.ViewGroup;

import com.github.tvbox.osc.base.BaseLazyFragment;

import java.util.List;
import java.util.HashMap;
import com.github.tvbox.osc.ui.fragment.GridFragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

/**
 * @user acer
 * @date 2018/12/4
 */

public class HomePageAdapter extends FragmentPagerAdapter {
    public FragmentManager fragmentManager;
    public List<BaseLazyFragment> list;
    private final HashMap<String, Long> pageIds = new HashMap<>();
    private long nextPageId = 1;

    public HomePageAdapter(FragmentManager fm) {
        super(fm);
    }

    public HomePageAdapter(FragmentManager fm, List<BaseLazyFragment> list) {
        super(fm);
        this.fragmentManager = fm;
        this.list = list;
    }

    public void clear() {
        list.clear();
        notifyDataSetChanged();
    }

    @Override
    public Fragment getItem(int position) {
        return list.get(position);
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        BaseLazyFragment page = list.get(position);
        if (!(page instanceof GridFragment)) return 0;
        String identity = ((GridFragment) page).getCategoryIdentity();
        Long id = pageIds.get(identity);
        if (id == null) {
            id = nextPageId++;
            pageIds.put(identity, id);
        }
        return id;
    }

    @Override
    public int getItemPosition(Object object) {
        int position = list.indexOf(object);
        return position < 0 ? POSITION_NONE : position;
    }

    @Override
    public Fragment instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment) super.instantiateItem(container, position);
        list.set(position, (BaseLazyFragment) fragment);
        fragmentManager.beginTransaction().show(fragment).commitAllowingStateLoss();
        return fragment;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        Fragment fragment = (Fragment) object;
        if (list.contains(fragment)) fragmentManager.beginTransaction().hide(fragment).commitAllowingStateLoss();
        else fragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss();
    }
}
