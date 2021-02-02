/**
 * Copyright 2020 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
