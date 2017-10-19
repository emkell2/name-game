package com.willowtreeapps.namegame.di;

import com.willowtreeapps.namegame.core.ApplicationComponent;
import com.willowtreeapps.namegame.ui.NameGameFragment;

import dagger.Component;

/**
 * Created by erin.kelley on 10/19/17.
 */
@FragmentScope
@Component(dependencies = ApplicationComponent.class, modules = {
        NameGameModule.class
})

public interface NameGameComponent {
    void inject(NameGameFragment fragment);
}
