/**
 * Copyright © 2016-2019 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.integration.custom.message;

public enum Unit {
    PRESSURE_MPA("1"), PRESSURE_BAR("2"), PRESSURE_KPA("3"), TEMPERATURE("4"), LEVEL_M("5"), FLOW("6"), ANGLE("7"), FLOAT("8");

    private String value;

    Unit(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Unit of(String value) {
        for (Unit u : Unit.values()) {
            if (u.getValue().equals(value)) {
                return u;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + value);
    }
}
