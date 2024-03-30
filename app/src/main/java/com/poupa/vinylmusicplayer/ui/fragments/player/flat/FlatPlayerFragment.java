package com.poupa.vinylmusicplayer.ui.fragments.player.flat;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.motion.widget.MotionLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.kabouzeid.appthemehelper.ThemeStore;
import com.kabouzeid.appthemehelper.util.ATHUtil;
import com.kabouzeid.appthemehelper.util.ColorUtil;
import com.poupa.vinylmusicplayer.R;
import com.poupa.vinylmusicplayer.adapter.base.MediaEntryViewHolder;
import com.poupa.vinylmusicplayer.databinding.FragmentFlatPlayerBinding;
import com.poupa.vinylmusicplayer.databinding.ItemListBinding;
import com.poupa.vinylmusicplayer.dialogs.SongShareDialog;
import com.poupa.vinylmusicplayer.helper.MusicPlayerRemote;
import com.poupa.vinylmusicplayer.helper.menu.SongMenuHelper;
import com.poupa.vinylmusicplayer.model.Song;
import com.poupa.vinylmusicplayer.ui.fragments.player.AbsPlayerFragment;
import com.poupa.vinylmusicplayer.util.MusicUtil;
import com.poupa.vinylmusicplayer.util.PlayingSongDecorationUtil;
import com.poupa.vinylmusicplayer.util.PreferenceUtil;
import com.poupa.vinylmusicplayer.util.Util;
import com.poupa.vinylmusicplayer.util.ViewUtil;
import com.poupa.vinylmusicplayer.util.VinylMusicPlayerColorUtil;
import com.poupa.vinylmusicplayer.views.WidthFitSquareLayout;

public class FlatPlayerFragment extends AbsPlayerFragment {
    private MotionLayout slidingUpPanel;
    private BottomSheetBehavior<MotionLayout> slidingUpPanelLayout;
    private BottomSheetBehavior.BottomSheetCallback slidingUpPanelCallback;

    private View playerStatusBar;
    private RecyclerView recyclerView;
    private TextView playerQueueSubHeader;

    private int lastColor;

    private FlatPlayerPlaybackControlsFragment playbackControlsFragment;

    private Impl impl;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (Util.isLandscape(getResources())) {
            impl = new LandscapeImpl(this);
        } else {
            impl = new PortraitImpl(this);
        }

        FragmentFlatPlayerBinding binding = FragmentFlatPlayerBinding.inflate(inflater, container, false);
        toolbar = binding.playerToolbar;
        playerStatusBar = binding.playerStatusBar;
        toolbarContainer = binding.toolbarContainer;
        slidingUpPanel = binding.playerSlidingLayout;
        slidingUpPanelLayout = (BottomSheetBehavior<MotionLayout>) ((CoordinatorLayout.LayoutParams) slidingUpPanel.getLayoutParams()).getBehavior();
        slidingUpPanelCallback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull final View view, final int i) {
                switch (i) {
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        onPanelCollapsed(view);
                        break;
//            case BottomSheetBehavior.STATE_ANCHORED:
//                slidingUpPanelLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED); // this fixes a bug where the panel would get stuck for some reason
//                break;
                }
            }

            @Override
            public void onSlide(@NonNull final View view, final float v) {
            }
        };
        recyclerView = binding.playerRecyclerView;
        playerQueueSubHeader = binding.playerQueueSubHeader;

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        impl.init();

        setUpPlayerToolbar();
        setUpSubFragments();

        setUpRecyclerView(recyclerView,slidingUpPanel);

        if (slidingUpPanelLayout != null) {
            slidingUpPanelLayout.addBottomSheetCallback(slidingUpPanelCallback);
//            slidingUpPanel.setAntiDragView(view.findViewById(R.id.draggable_area));
        }

        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                impl.setUpPanelAndAlbumCoverHeight();
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (slidingUpPanelLayout != null) {
            slidingUpPanelLayout.removeBottomSheetCallback(slidingUpPanelCallback);
        }

        if (recyclerView != null) {
            recyclerView.setItemAnimator(null);
            recyclerView.setAdapter(null);
            recyclerView = null;
        }

        super.onDestroyView();
    }

    @Override
    public void onPause() {
        if (recyclerViewDragDropManager != null) {
            recyclerViewDragDropManager.cancelDrag();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkToggleToolbar(toolbarContainer);
    }

    @Override
    public void onServiceConnected() {
        updateQueue();
        updateCurrentSong();
        updateIsFavorite();
        updateLyrics();
    }

    @Override
    public void onPlayingMetaChanged() {
        updateCurrentSong();
        updateIsFavorite();
        updateQueuePosition();
        updateLyrics();
    }

    @Override
    public void onPlayStateChanged() {
        updateCurrentSong();
    }

    @Override
    public void onQueueChanged() {
        updateQueue();
    }

    private void updateQueue() {
        playingQueueAdapter.swapDataSet(MusicPlayerRemote.getPlayingQueue(), MusicPlayerRemote.getPosition());
        playerQueueSubHeader.setText(MusicPlayerRemote.getQueueInfoString());
        if (slidingUpPanelLayout == null || slidingUpPanelLayout.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            resetToCurrentPosition();
        }
    }

    private void updateQueuePosition() {
        playingQueueAdapter.setCurrent(MusicPlayerRemote.getPosition());
        playerQueueSubHeader.setText(MusicPlayerRemote.getQueueInfoString());
        if (slidingUpPanelLayout == null || slidingUpPanelLayout.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
            resetToCurrentPosition();
        }
    }

    private void updateCurrentSong() {
        impl.updateCurrentSong(MusicPlayerRemote.getCurrentSong());

        // give the adapter a chance to update the decoration
        recyclerView.getAdapter().notifyItemChanged(MusicPlayerRemote.getPosition());
    }

    @Override
    protected void setUpSubFragments() {
        playbackControlsFragment = (FlatPlayerPlaybackControlsFragment) getChildFragmentManager().findFragmentById(R.id.playback_controls_fragment);
        super.setUpSubFragments();
    }

    protected void setUpPlayerToolbar() {
        toolbar.inflateMenu(R.menu.menu_player);
        toolbar.setNavigationIcon(R.drawable.ic_close_white_24dp);
        toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
        toolbar.setOnMenuItemClickListener(this);

        super.setUpPlayerToolbar();
    }

    @Override
    @ColorInt
    public int getPaletteColor() {
        return lastColor;
    }

    private void animateColorChange(final int newColor) {
        impl.animateColorChange(newColor);
        lastColor = newColor;
    }

    @Override
    public void onShow() {
        playbackControlsFragment.show();
    }

    @Override
    public void onHide() {
        playbackControlsFragment.hide();
        onBackPressed();
    }

    @Override
    public boolean onBackPressed() {
        boolean wasExpanded = false;
        if (slidingUpPanelLayout != null) {
            wasExpanded = slidingUpPanelLayout.getState() == BottomSheetBehavior.STATE_EXPANDED;
            slidingUpPanelLayout.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

        return wasExpanded;
    }

    @Override
    public void onColorChanged(int color) {
        animateColorChange(color);
        playbackControlsFragment.setDark(ColorUtil.isColorLight(color));

        super.onColorChanged(color);
    }

    public void onPanelCollapsed(View panel) {
        resetToCurrentPosition();
    }

    private void resetToCurrentPosition() {
        recyclerView.stopScroll();
        layoutManager.scrollToPositionWithOffset(MusicPlayerRemote.getPosition() + 1, 0);
    }

    interface Impl {
        void init();

        void updateCurrentSong(@NonNull final Song song);

        void animateColorChange(final int newColor);

        void setUpPanelAndAlbumCoverHeight();
    }

    private abstract static class BaseImpl implements Impl {
        protected FlatPlayerFragment fragment;

        public BaseImpl(FlatPlayerFragment fragment) {
            this.fragment = fragment;
        }

        public AnimatorSet createDefaultColorChangeAnimatorSet(int newColor) {
            Animator backgroundAnimator = ViewUtil.createBackgroundColorTransition(fragment.playbackControlsFragment.getView(), fragment.lastColor, newColor);
            Animator statusBarAnimator = ViewUtil.createBackgroundColorTransition(fragment.playerStatusBar, fragment.lastColor, newColor);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(backgroundAnimator, statusBarAnimator);

            int adjustedLastColor = fragment.lastColor;
            int adjustedNewColor = newColor;

            int backgroundColor = ATHUtil.resolveColor(fragment.requireActivity(), R.attr.cardBackgroundColor);
            adjustedLastColor = VinylMusicPlayerColorUtil.getContrastedColor(adjustedLastColor, backgroundColor);
            adjustedNewColor = VinylMusicPlayerColorUtil.getContrastedColor(adjustedNewColor, backgroundColor);
            Animator subHeaderAnimator = ViewUtil.createTextColorTransition(fragment.playerQueueSubHeader, adjustedLastColor, adjustedNewColor);
            animatorSet.play(subHeaderAnimator);

            // Workaround for a bug https://github.com/AdrienPoupa/VinylMusicPlayer/issues/620
            for (Animator animator : animatorSet.getChildAnimations()){
                animator.setDuration(ViewUtil.VINYL_MUSIC_PLAYER_ANIM_TIME);
            }

            return animatorSet;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static class PortraitImpl extends BaseImpl {
        MediaEntryViewHolder currentSongViewHolder;
        Song currentSong = Song.EMPTY_SONG;

        public PortraitImpl(FlatPlayerFragment fragment) {
            super(fragment);
        }

        @Override
        public void init() {
            ItemListBinding binding = ItemListBinding.bind(fragment.getView().findViewById(R.id.current_song));
            currentSongViewHolder = new MediaEntryViewHolder(binding);

            currentSongViewHolder.separator.setVisibility(View.VISIBLE);
            currentSongViewHolder.shortSeparator.setVisibility(View.GONE);
            currentSongViewHolder.image.setScaleType(ImageView.ScaleType.CENTER);
            currentSongViewHolder.image.setColorFilter(ATHUtil.resolveColor(fragment.getActivity(), R.attr.iconColor, ThemeStore.textColorSecondary(fragment.getActivity())), PorterDuff.Mode.SRC_IN);
            currentSongViewHolder.image.setImageResource(PlayingSongDecorationUtil.sIconPlaying);
            currentSongViewHolder.itemView.setOnClickListener(v -> {
                // toggle the panel
                if (fragment.slidingUpPanelLayout.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                    fragment.slidingUpPanelLayout.setState(BottomSheetBehavior.STATE_EXPANDED);
                } else if (fragment.slidingUpPanelLayout.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    fragment.slidingUpPanelLayout.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            });
            currentSongViewHolder.menu.setOnClickListener(new SongMenuHelper.OnClickSongMenu((AppCompatActivity) fragment.getActivity()) {
                @Override
                public Song getSong() {
                    return currentSong;
                }

                public int getMenuRes() {
                    return R.menu.menu_item_playing_queue_song;
                }

                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    final int itemId = item.getItemId();
                    if (itemId == R.id.action_remove_from_playing_queue) {
                        MusicPlayerRemote.removeFromQueue(MusicPlayerRemote.getPosition());
                        return true;
                    } else if (itemId == R.id.action_share) {
                        SongShareDialog.create(getSong()).show(fragment.getParentFragmentManager(), "SONG_SHARE_DIALOG");
                        return true;
                    }
                    return super.onMenuItemClick(item);
                }
            });
            currentSongViewHolder.title.setTypeface(null, Typeface.BOLD);
        }

        @Override
        public void setUpPanelAndAlbumCoverHeight() {
            WidthFitSquareLayout albumCoverContainer = fragment.getView().findViewById(R.id.album_cover_container);

            final int availablePanelHeight = fragment.slidingUpPanel.getHeight() - fragment.getView().findViewById(R.id.player_content).getHeight();
            final int minPanelHeight = (int) ViewUtil.convertDpToPixel(8 + 72 + 24, fragment.getResources()) + fragment.getResources().getDimensionPixelSize(R.dimen.progress_container_height) + fragment.getResources().getDimensionPixelSize(R.dimen.media_controller_container_height);
            if (availablePanelHeight < minPanelHeight) {
                albumCoverContainer.getLayoutParams().height = albumCoverContainer.getHeight() - (minPanelHeight - availablePanelHeight);
                albumCoverContainer.forceSquare(false);
            }
            fragment.slidingUpPanel.setMinHeight(Math.max(minPanelHeight, availablePanelHeight));
            //fragment.slidingUpPanel.setMaxHeight(Math.max(minPanelHeight, availablePanelHeight));

//            ((AbsSlidingMusicPanelActivity) fragment.getActivity()).setAntiDragView(fragment.slidingUpPanel.findViewById(R.id.player_panel));
        }

        @Override
        public void updateCurrentSong(@NonNull final Song song) {
            currentSong = song;
            currentSongViewHolder.title.setText(song.getTitle());
            currentSongViewHolder.text.setText(MusicUtil.getSongInfoString(song));

            if (PreferenceUtil.getInstance().animatePlayingSongIcon()) {
                final boolean isPlaying = MusicPlayerRemote.isPlaying(song);
                if (isPlaying) {
                    currentSongViewHolder.image.startAnimation(PlayingSongDecorationUtil.sIconAnimation);
                } else {
                    currentSongViewHolder.image.clearAnimation();
                }
            }
        }

        @Override
        public void animateColorChange(int newColor) {
            createDefaultColorChangeAnimatorSet(newColor).start();
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static class LandscapeImpl extends BaseImpl {
        public LandscapeImpl(FlatPlayerFragment fragment) {
            super(fragment);
        }

        @Override
        public void init() {
        }

        @Override
        public void setUpPanelAndAlbumCoverHeight() {
//            ((AbsSlidingMusicPanelActivity) fragment.getActivity()).setAntiDragView(fragment.getView().findViewById(R.id.player_panel));
        }

        @Override
        public void updateCurrentSong(@NonNull final Song song) {
            fragment.toolbar.setTitle(song.getTitle());
            fragment.toolbar.setSubtitle(MusicUtil.getSongInfoString(song));
        }

        @Override
        public void animateColorChange(int newColor) {
            AnimatorSet animatorSet = createDefaultColorChangeAnimatorSet(newColor);
            animatorSet.play(ViewUtil.createBackgroundColorTransition(fragment.toolbar, fragment.lastColor, newColor));
            animatorSet.start();
        }
    }
}
