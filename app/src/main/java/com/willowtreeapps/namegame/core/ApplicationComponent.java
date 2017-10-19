package com.willowtreeapps.namegame.core;

import com.squareup.picasso.Picasso;
import com.willowtreeapps.namegame.network.NetworkModule;
import com.willowtreeapps.namegame.network.api.ProfilesRepository;
import com.willowtreeapps.namegame.ui.NameGameActivity;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
        ApplicationModule.class,
        NetworkModule.class
})
public interface ApplicationComponent {
    void inject(NameGameActivity activity);
    ListRandomizer listRandomizer();
    Picasso picasso();
    ProfilesRepository profileRepository();
}