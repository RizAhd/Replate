package com.replate.app.models;

import androidx.annotation.DrawableRes;

import com.google.firebase.database.Exclude;

/**
 * One promo banner on the Home screen. Server banners live under {@code banners/{id}} in the
 * Realtime Database; the bundled starter banners use {@link #localImageRes} instead of an image URL.
 */
public class BannerItem {

    private String id;
    private String title;
    private String subtitle;
    private String badge;
    private String imageUrl;
    private String deepLink;
    private long order;
    private boolean active = true;
    /** Epoch millis; 0 means "no start limit". */
    private long startAt;
    /** Epoch millis; 0 means "no end limit". */
    private long endAt;

    /** Not stored in Firebase: drawable used for the bundled fallback banners. */
    private transient int localImageRes;

    /** Required by Firebase. */
    public BannerItem() {
    }

    public BannerItem(String id, String badge, String title, String subtitle,
                      @DrawableRes int localImageRes, String deepLink) {
        this.id = id;
        this.badge = badge;
        this.title = title;
        this.subtitle = subtitle;
        this.localImageRes = localImageRes;
        this.deepLink = deepLink;
    }

    /** True when the banner is switched on and today falls inside its optional date window. */
    public boolean isLiveAt(long nowMillis) {
        if (!active) {
            return false;
        }
        if (startAt > 0 && nowMillis < startAt) {
            return false;
        }
        return endAt <= 0 || nowMillis <= endAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getDeepLink() { return deepLink; }
    public void setDeepLink(String deepLink) { this.deepLink = deepLink; }

    public long getOrder() { return order; }
    public void setOrder(long order) { this.order = order; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getStartAt() { return startAt; }
    public void setStartAt(long startAt) { this.startAt = startAt; }

    public long getEndAt() { return endAt; }
    public void setEndAt(long endAt) { this.endAt = endAt; }

    @Exclude
    @DrawableRes
    public int getLocalImageRes() { return localImageRes; }

    @Exclude
    public void setLocalImageRes(@DrawableRes int localImageRes) { this.localImageRes = localImageRes; }
}
