package com.willowtreeapps.namegame.ui;

import android.content.Context;

import com.willowtreeapps.namegame.network.api.model.Person;

import java.util.List;

/**
 * Created by erin.kelley on 10/18/17.
 */

public interface ViewContract {
    void onNewData(List<Person> people);
    Context getContext();
}
