package com.ankit.syncmesh.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.ankit.syncmesh.R;
import com.ankit.syncmesh.data.AppRepository;
import com.ankit.syncmesh.databinding.ActivityOnboardingBinding;

/**
 * First-run educational setup guide. Purely presentational: it explains how SyncMesh works
 * (sync, permissions, pairing, keyboard, privacy) and records a "seen" flag in preferences.
 * It changes no business logic.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int[] ICONS = {
            R.drawable.ic_sync,       // welcome
            R.drawable.ic_devices,    // what it does
            R.drawable.ic_check,      // permissions
            R.drawable.ic_copy,       // clipboard sync
            R.drawable.ic_pair,       // pairing
            R.drawable.ic_keyboard,   // keyboard add-on
            R.drawable.ic_key,        // privacy
            R.drawable.ic_rocket      // ready
    };
    private static final int[] TITLES = {
            R.string.onboarding_1_title, R.string.onboarding_2_title,
            R.string.onboarding_3_title, R.string.onboarding_4_title,
            R.string.onboarding_5_title, R.string.onboarding_6_title,
            R.string.onboarding_7_title, R.string.onboarding_8_title
    };
    private static final int[] DESCS = {
            R.string.onboarding_1_desc, R.string.onboarding_2_desc,
            R.string.onboarding_3_desc, R.string.onboarding_4_desc,
            R.string.onboarding_5_desc, R.string.onboarding_6_desc,
            R.string.onboarding_7_desc, R.string.onboarding_8_desc
    };

    private ActivityOnboardingBinding binding;
    private final PagerSnapHelper snapHelper = new PagerSnapHelper();
    private LinearLayoutManager layoutManager;
    private int currentPage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets();

        layoutManager = new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false);
        binding.pager.setLayoutManager(layoutManager);
        binding.pager.setAdapter(new PageAdapter());
        snapHelper.attachToRecyclerView(binding.pager);

        buildDots();
        updatePage(0);

        binding.pager.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snap = snapHelper.findSnapView(layoutManager);
                    if (snap != null) {
                        updatePage(layoutManager.getPosition(snap));
                    }
                }
            }
        });

        binding.buttonSkip.setOnClickListener(v -> finishOnboarding());
        binding.buttonBack.setOnClickListener(v -> {
            if (currentPage > 0) {
                binding.pager.smoothScrollToPosition(currentPage - 1);
            }
        });
        binding.buttonNext.setOnClickListener(v -> {
            if (currentPage >= TITLES.length - 1) {
                finishOnboarding();
            } else {
                binding.pager.smoothScrollToPosition(currentPage + 1);
            }
        });
    }

    private void finishOnboarding() {
        AppRepository.getInstance(this).getPreferences().setOnboardingComplete(true);
        finish();
    }

    private void updatePage(int page) {
        currentPage = page;
        boolean last = page >= TITLES.length - 1;
        binding.textStepCounter.setText(getString(R.string.onboarding_step_counter,
                page + 1, TITLES.length));
        binding.buttonNext.setText(last ? R.string.onboarding_get_started : R.string.onboarding_next);
        binding.buttonSkip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        // First page shows a single full-width Next button; GONE (not INVISIBLE) so the
        // hidden Back button does not reserve empty space in the row.
        boolean first = page == 0;
        binding.buttonBack.setVisibility(first ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams nextParams =
                (LinearLayout.LayoutParams) binding.buttonNext.getLayoutParams();
        nextParams.setMarginStart(first
                ? 0 : getResources().getDimensionPixelSize(R.dimen.space_sm));
        binding.buttonNext.setLayoutParams(nextParams);
        for (int i = 0; i < binding.dots.getChildCount(); i++) {
            View dot = binding.dots.getChildAt(i);
            boolean active = i == page;
            dot.setBackgroundResource(active
                    ? R.drawable.dot_page_active
                    : R.drawable.dot_page_inactive);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) dot.getLayoutParams();
            params.width = dp(active ? 24 : 8);
            params.height = dp(8);
            dot.setLayoutParams(params);
        }
    }

    private void buildDots() {
        int margin = dp(4);
        for (int i = 0; i < TITLES.length; i++) {
            View dot = new View(this);
            // Plain Views ignore WRAP_CONTENT (they fill available space), so the
            // indicator sizes must be explicit pixels.
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(8), dp(8));
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.dot_page_inactive);
            binding.dots.addView(dot);
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageViewHolder> {
        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding, parent, false);
            // Each page fills the horizontal viewport so PagerSnapHelper snaps per page.
            item.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new PageViewHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            holder.icon.setImageResource(ICONS[position]);
            holder.title.setText(TITLES[position]);
            holder.desc.setText(DESCS[position]);
        }

        @Override
        public int getItemCount() {
            return TITLES.length;
        }
    }

    private static final class PageViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView desc;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.image_onboarding);
            title = itemView.findViewById(R.id.text_onboarding_title);
            desc = itemView.findViewById(R.id.text_onboarding_desc);
        }
    }
}
