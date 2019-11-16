package com.epam.test;

import com.epam.drill.builds.Build2;

public class Test implements Build2.Test {
    public void firstMethod() {
        System.out.println("firstMethod called");
    }

    @Override
    public void test1() {
        firstMethod();
    }

    @Override
    public void test2() {

    }

    @Override
    public void test3() {

    }
}
