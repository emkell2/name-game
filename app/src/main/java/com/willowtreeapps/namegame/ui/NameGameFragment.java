package com.willowtreeapps.namegame.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.willowtreeapps.namegame.R;
import com.willowtreeapps.namegame.core.ListRandomizer;
import com.willowtreeapps.namegame.core.NameGameApplication;
import com.willowtreeapps.namegame.di.DaggerNameGameComponent;
import com.willowtreeapps.namegame.di.NameGameModule;
import com.willowtreeapps.namegame.network.api.model.Person;
import com.willowtreeapps.namegame.presenters.NameGamePresenter;
import com.willowtreeapps.namegame.util.CircleBorderTransform;
import com.willowtreeapps.namegame.util.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class NameGameFragment extends Fragment implements ViewContract {

    private static final Interpolator OVERSHOOT = new OvershootInterpolator();

    @Inject
    ListRandomizer listRandomizer;
    @Inject
    Picasso picasso;
    @Inject
    NameGamePresenter mPresenter;

    private TextView title;
    private TextView score;
    private TextView time;
    private ViewGroup container;

    private static final int MS_TILL_NEW_DATA = 1000;
    private static final int MS_HIDE_A_FACE = 5000;
    private static final int NUM_FACES = 5;

    private List<ImageView> faces = new ArrayList<>(NUM_FACES);

    private int imagesLoaded;
    private int numFacesShowing;
    private int correctIndex = -1;
    private int currScore = 0;
    private int numCorrect;
    private long averageTime;   // In seconds
    private long totalTime;     // In seconds
    private long timeStart;     // In millis

    private boolean orientationChange;
    private boolean pickedCorrect;
    private boolean hideMode;

    private static final String HTTPS_PREFIX = "https:";
    private static final String SCORE_STR = "Score: ";

    private List<Person> mRandomPeople;
    private Person mRandomPerson;

    ScheduledExecutorService mScheduleTaskExecutor;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DaggerNameGameComponent.builder()
                .applicationComponent(NameGameApplication.get(getActivity()).component())
                .nameGameModule(new NameGameModule(this))
                .build()
                .inject(this);

        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.name_game_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        title = (TextView) view.findViewById(R.id.title);
        score = (TextView) view.findViewById(R.id.score);
        time = (TextView) view.findViewById(R.id.time);
        container = (ViewGroup) view.findViewById(R.id.face_container);

        final TextView tvHideMode = (TextView) view.findViewById(R.id.tvhideMode);
        tvHideMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (hideMode) {
                    tvHideMode.setText(getString(R.string.hide));
                } else {
                    tvHideMode.setText(getString(R.string.unhide));
                }

                hideMode = !hideMode;
            }
        });

        //Hide the views until data loads
        title.setAlpha(0);

        // Set initial average time
        updateTime(0);

        int n = container.getChildCount();
        for (int i = 0; i < n; i++) {
            ImageView face = (ImageView) container.getChildAt(i);
            face.setTag(i);
            face.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getTag() instanceof Integer) {
                        int pos = (Integer) v.getTag();
                        onPersonSelected(v, pos, mRandomPeople.get(pos));
                    }
                }
            });
            faces.add(face);

            //Hide the views until data loads
            face.setScaleX(0);
            face.setScaleY(0);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mRandomPeople != null) {
            outState.putParcelableArrayList(getString(R.string.random_people),
                    new ArrayList<>(mRandomPeople));
        }

        outState.putParcelable(getString(R.string.random_person), mRandomPerson);
        outState.putInt(getString(R.string.score), currScore);
        outState.putInt(getString(R.string.num_correct), numCorrect);
        outState.putLong(getString(R.string.avg_time), averageTime);
        outState.putLong(getString(R.string.total_time), totalTime);
        outState.putLong(getString(R.string.start_time), timeStart);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            orientationChange = true;

            mRandomPeople = (List<Person>) savedInstanceState.get(getString(R.string.random_people));
            mRandomPerson = (Person) savedInstanceState.get(getString(R.string.random_person));
            currScore = savedInstanceState.getInt(getString(R.string.score));
            numCorrect = savedInstanceState.getInt(getString(R.string.num_correct));
            averageTime = savedInstanceState.getLong(getString(R.string.avg_time));
            totalTime = savedInstanceState.getLong(getString(R.string.total_time));
            timeStart = savedInstanceState.getLong(getString(R.string.start_time));

            // This along with some other data needs to be bundled up when the orientation is changed
            // For time saving, I'm doing this to prevent crashing when orientation is changed and
            // you are in hide mode. There are issues with hide mode in landscape mode because I ran
            // out of time to implement it, but it would be easy to do.
            numCorrect = NUM_FACES;

            updateTime(averageTime);
            setViews();
        }
    }

    @Override
    public void onNewData(List<Person> people) {
        // Get random people and person to choose
        mRandomPeople = getRandomPeople(people);
        mRandomPerson = getRandomPerson(mRandomPeople);

        setViews();
    }

    @Override
    public void onResume() {
        super.onResume();
        orientationChange = false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        faces.clear();
        mRandomPeople = null;
        mRandomPerson = null;
        imagesLoaded = 0;
    }

    private void setViews() {
        updateScore(currScore);

        // Show title
        if (mRandomPerson != null) {
            title.setText(mRandomPerson.getFirstName() + " " + mRandomPerson.getLastName());
        }

        setImages(faces, mRandomPeople);
        correctIndex = getRandomPersonIndex();
    }

    private List<Person> getRandomPeople(List<Person> people){
        return listRandomizer.pickN(people, NUM_FACES);
    }

    private Person getRandomPerson(List<Person> people) {
        return listRandomizer.pickOne(people);
    }

    private int getRandomPersonIndex() {
        int index = 0;
        if (mRandomPeople != null) {
            for (Person person : mRandomPeople) {
                if (mRandomPerson.getFirstName().equals(person.getFirstName())
                        && mRandomPerson.getLastName().equals(person.getLastName())) {
                    return index;
                }
                index++;
            }
        }
        return -1;
    }

    /**
     * A method for setting the images from people into the imageviews
     */
    private void setImages(final List<ImageView> faces, List<Person> people) {
        int imageSize = (int) Ui.convertDpToPixel(100, getContext());
        int n = faces.size();
        imagesLoaded = 0;

        for (int i = 0; i < n; i++) {
            ImageView face = faces.get(i);
            if (!face.isShown()) {
                face.setVisibility(View.VISIBLE);
            }

            if (people != null && people.get(i).getHeadshot() != null) {
                String url = people.get(i).getHeadshot().getUrl();
                picasso.load(HTTPS_PREFIX + url)
                        .placeholder(R.drawable.ic_face_white_48dp)
                        .resize(imageSize, imageSize)
                        .transform(new CircleBorderTransform())
                        .into(face, new com.squareup.picasso.Callback() {
                            @Override
                            public void onSuccess() {
                                incrementImagesLoaded();

                                if (imagesLoaded == faces.size()) {
                                    animateFacesIn();
                                }
                            }

                            @Override
                            public void onError() {
                                Log.d("Error", "Error loading an image");
                                incrementImagesLoaded();

                                if (imagesLoaded == faces.size()) {
                                    animateFacesIn();
                                }
                            }
                        });
            }
        }
    }

    private void incrementImagesLoaded() {
        imagesLoaded++;
    }

    /**
     * A method to animate the faces into view
     */
    private void animateFacesIn() {
        title.animate().alpha(1).start();
        for (int i = 0; i < faces.size(); i++) {
            ImageView face = faces.get(i);
            face.animate().scaleX(1).scaleY(1).setStartDelay(800 + 120 * i).setInterpolator(OVERSHOOT).start();
        }

        if (!orientationChange) {
            timeStart = SystemClock.elapsedRealtime();
        }

        numFacesShowing = NUM_FACES;

        if (hideMode) {
            hideFacesHintMode();
        }
    }

    private void hideFacesHintMode() {
        final ArrayList<ImageView> facesLeft = new ArrayList<>(faces);

        mScheduleTaskExecutor = Executors.newScheduledThreadPool(NUM_FACES);
        mScheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if ((!pickedCorrect) && (numFacesShowing > 1)) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int i = getRandomIncorrectIndex(facesLeft);
                            facesLeft.get(i).setVisibility(View.GONE);
                            facesLeft.remove(i);
                            numFacesShowing--;
                        }
                    });
                } else {
                    getScheduleTaskExecutor().shutdownNow();
                }
            }
        }, MS_HIDE_A_FACE, MS_HIDE_A_FACE, TimeUnit.MILLISECONDS);
    }

    private int getRandomIncorrectIndex(ArrayList<ImageView> facesLeft) {
        int index;
        while (((index = new Random().nextInt(numFacesShowing)) == correctIndex)
                || ((int) facesLeft.get(index).getTag() == correctIndex)) {
            ;
        }
        return index;
    }

    /**
     * A method to handle when a person is selected
     *
     * @param view   The view that was selected
     * @param person The person that was selected
     */
    private void onPersonSelected(@NonNull View view, int pos, @NonNull Person person) {
        ImageView face = (ImageView) view;
        if (pos == correctIndex) {
            pickedCorrect = true;

            face.setColorFilter(ContextCompat.getColor(getContext(), R.color.alphaGreen),
                    android.graphics.PorterDuff.Mode.MULTIPLY);

            updateScore(++currScore);
            processAverageTime();

            loadNewData();
        } else {
            face.setColorFilter(ContextCompat.getColor(getContext(), R.color.alphaRed),
                    android.graphics.PorterDuff.Mode.MULTIPLY);
            updateScore(--currScore);
        }

    }

    private void processAverageTime() {
        long timeEnd = SystemClock.elapsedRealtime();
        long delta = timeEnd - timeStart;
        totalTime += (delta / 1000);
        averageTime = totalTime / ++numCorrect;
        updateTime(averageTime);
    }

    private void updateScore(int newScore) {
        if (score != null) {
            score.setText(SCORE_STR + newScore);
        }
    }

    private void updateTime(long averageTime) {
        time.setText(getString(R.string.time) + " " + averageTime + " " + getString(R.string.seconds));
    }

    private void loadNewData() {
        if (mScheduleTaskExecutor != null && !mScheduleTaskExecutor.isShutdown()) {
            mScheduleTaskExecutor.shutdownNow();
        }

        pickedCorrect = false;

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mPresenter.loadData();
                for (ImageView face : faces) {
                    face.clearColorFilter();
                }
            }
        }, MS_TILL_NEW_DATA);
    }

    @Override
    public Context getContext() {
        return super.getContext();
    }

    public ScheduledExecutorService getScheduleTaskExecutor() {
        return mScheduleTaskExecutor;
    }
}
