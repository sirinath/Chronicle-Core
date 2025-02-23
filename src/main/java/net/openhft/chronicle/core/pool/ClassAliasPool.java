/*
 *     Copyright (C) 2015  higherfrequencytrading.com
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.core.pool;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClassAliasPool {
    private final Map<String, Class> stringClassMap = new ConcurrentHashMap<>();
    private final Map<String, Class> stringClassMap2 = new ConcurrentHashMap<>();
    private final Map<Class, String> classStringMap = new ConcurrentHashMap<>();
    public static final ClassAliasPool CLASS_ALIASES = new ClassAliasPool().defaultAliases();

    private ClassAliasPool defaultAliases() {
        addAlias(Set.class);
        addAlias(String.class, "String, !str");
        addAlias(CharSequence.class);
        addAlias(Byte.class, "Byte, int8");
        addAlias(Character.class, "Char");
        addAlias(Integer.class, "int32");
        addAlias(Long.class, "Int, int64");
        addAlias(Float.class, "Float32");
        addAlias(Double.class, "Float64");
        addAlias(LocalDate.class, "Date");
        addAlias(LocalDateTime.class, "DateTime");
        addAlias(LocalTime.class, "Time");
        addAlias(String[].class, "String[]");


        return this;
    }

    /**
     * remove classes which are not in the default class loaders.
     */
    public void clean() {
        clean(stringClassMap.values());
        clean(stringClassMap2.values());
        clean(classStringMap.keySet());
    }

    private void clean(Iterable<Class> coll) {
        ClassLoader classLoader2 = ClassAliasPool.class.getClassLoader();
        for (Iterator<Class> iter = coll.iterator(); iter.hasNext(); ) {
            Class clazz = iter.next();
            ClassLoader classLoader = clazz.getClassLoader();
            if (classLoader == null || classLoader == classLoader2)
                continue;
            iter.remove();
        }
    }

    public Class forName(CharSequence name) throws IllegalArgumentException {
        String name0 = name.toString();
        Class clazz = stringClassMap.get(name0);
        return clazz != null
                ? clazz
                : stringClassMap2.computeIfAbsent(name0, n -> {
            try {
                return Class.forName(name0);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        });
    }

    public String nameFor(Class clazz) {

        return classStringMap.computeIfAbsent(clazz, (aClass) -> {
            if (Enum.class.isAssignableFrom(aClass)) {
                Class clazz2 = aClass.getEnclosingClass();
                if (clazz2 != null) {
                    aClass = clazz2;
                    String alias = classStringMap.get(clazz2);
                    if (alias != null) return alias;
                }
            }
            return aClass.getName();
        });
    }

    public void addAlias(Class... classes) {
        for (Class clazz : classes) {
            stringClassMap.putIfAbsent(clazz.getName(), clazz);
            stringClassMap2.putIfAbsent(clazz.getSimpleName(), clazz);
            stringClassMap2.putIfAbsent(toCamelCase(clazz.getSimpleName()), clazz);
            classStringMap.computeIfAbsent(clazz, Class::getSimpleName);
        }
    }

    // to lower camel case.
    private String toCamelCase(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    public void addAlias(Class clazz, String names) {
        for (String name : names.split(", ?")) {
            stringClassMap.put(name, clazz);
            stringClassMap2.putIfAbsent(toCamelCase(name), clazz);
            classStringMap.putIfAbsent(clazz, name);
            addAlias(clazz);
        }
    }
}
