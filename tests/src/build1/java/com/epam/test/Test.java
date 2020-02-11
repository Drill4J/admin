package com.epam.test;


import com.epam.drill.builds.Build1;

public class Test implements Build1.Test {
    @Override
    public void test1() {
        System.out.println("test1");
    }

    @Override
    public void test2() {
        System.out.println("test2");
    }

    @Override
    public void test3() {
        System.out.println("test3");
    }
}
