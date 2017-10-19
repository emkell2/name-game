package com.willowtreeapps.namegame.di;

import android.support.annotation.NonNull;

import com.willowtreeapps.namegame.network.api.ProfilesRepository;
import com.willowtreeapps.namegame.presenters.NameGamePresenter;
import com.willowtreeapps.namegame.ui.ViewContract;

import dagger.Module;
import dagger.Provides;

/**
 * Created by erin.kelley on 10/19/17.
 */

@Module
public class NameGameModule {
    ViewContract mView;

    public NameGameModule(ViewContract view) {
        this.mView = view;
    }

    @Provides @NonNull @FragmentScope
    public NameGamePresenter providePresenter(@NonNull ProfilesRepository repository) {
        return new NameGamePresenter(mView, repository);
    }
}
