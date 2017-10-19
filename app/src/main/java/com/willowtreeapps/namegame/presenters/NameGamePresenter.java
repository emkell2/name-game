package com.willowtreeapps.namegame.presenters;

import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.willowtreeapps.namegame.network.api.ProfilesRepository;
import com.willowtreeapps.namegame.network.api.model.Person;
import com.willowtreeapps.namegame.ui.ViewContract;

import java.util.List;

/**
 * Created by erin.kelley on 10/18/17.
 */

public class NameGamePresenter implements PresenterContract {
    private final String DEBUG_FAILURE_TAG = "FAILURE";

    private ViewContract mView;
    private ProfilesRepository mRepository;

    public NameGamePresenter(ViewContract view, ProfilesRepository repository) {
        this.mView = view;
        this.mRepository = repository;

        registerListener();
    }

    @Override
    public void loadData() {
        registerListener();
        mRepository.load();
    }

    private void registerListener() {
        mRepository.register(new ProfilesRepository.Listener() {
            @Override
            public void onLoadFinished(@NonNull List<Person> people) {
                if (mView != null) {
                    mView.onNewData(people);
                }
                mRepository.unregister(this);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                Log.d(DEBUG_FAILURE_TAG, error.toString());
                Toast.makeText(mView.getContext(), "error while loading data",
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
