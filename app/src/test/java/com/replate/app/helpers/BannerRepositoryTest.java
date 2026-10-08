package com.replate.app.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.replate.app.models.BannerItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Checks which home banners are shown and in what order.
public class BannerRepositoryTest {

    private static final long NOW = 1_700_000_000_000L;

    private static BannerItem banner(String id, long order, boolean active, long startAt, long endAt) {
        BannerItem item = new BannerItem();
        item.setId(id);
        item.setTitle("Title " + id);
        item.setOrder(order);
        item.setActive(active);
        item.setStartAt(startAt);
        item.setEndAt(endAt);
        return item;
    }

    private static List<String> ids(List<BannerItem> banners) {
        List<String> ids = new ArrayList<>();
        for (BannerItem banner : banners) {
            ids.add(banner.getId());
        }
        return ids;
    }

    @Test
    public void sortsByOrderField() {
        List<BannerItem> result = BannerRepository.filterLiveAndSort(Arrays.asList(
                banner("c", 3, true, 0, 0),
                banner("a", 1, true, 0, 0),
                banner("b", 2, true, 0, 0)), NOW);

        assertEquals(Arrays.asList("a", "b", "c"), ids(result));
    }

    @Test
    public void dropsInactiveBanners() {
        List<BannerItem> result = BannerRepository.filterLiveAndSort(Arrays.asList(
                banner("on", 1, true, 0, 0),
                banner("off", 2, false, 0, 0)), NOW);

        assertEquals(Arrays.asList("on"), ids(result));
    }

    @Test
    public void respectsStartAndEndWindow() {
        List<BannerItem> result = BannerRepository.filterLiveAndSort(Arrays.asList(
                banner("notYet", 1, true, NOW + 1000, 0),
                banner("expired", 2, true, 0, NOW - 1000),
                banner("current", 3, true, NOW - 1000, NOW + 1000),
                banner("openEnded", 4, true, NOW - 1000, 0)), NOW);

        assertEquals(Arrays.asList("current", "openEnded"), ids(result));
    }

    @Test
    public void dropsBannersWithNothingToShow() {
        BannerItem empty = new BannerItem();
        empty.setId("empty");
        empty.setActive(true);

        BannerItem onlyImage = new BannerItem();
        onlyImage.setId("imageOnly");
        onlyImage.setImageUrl("https://example.com/a.jpg");

        List<BannerItem> result = BannerRepository.filterLiveAndSort(Arrays.asList(empty, onlyImage), NOW);

        assertEquals(Arrays.asList("imageOnly"), ids(result));
    }

    @Test
    public void toleratesNullListAndNullEntries() {
        assertTrue(BannerRepository.filterLiveAndSort(null, NOW).isEmpty());
        assertTrue(BannerRepository.filterLiveAndSort(Arrays.asList((BannerItem) null), NOW).isEmpty());
    }

    @Test
    public void isLiveAtBoundaries() {
        BannerItem item = banner("x", 1, true, NOW, NOW + 10);

        assertFalse(item.isLiveAt(NOW - 1));
        assertTrue(item.isLiveAt(NOW));
        assertTrue(item.isLiveAt(NOW + 10));
        assertFalse(item.isLiveAt(NOW + 11));
    }
}
