/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Testing parsing of a matcher method.
 * @enablePreview
 * @build testdata.*
 * @run junit MatcherTest
 */

import helpers.ClassRecord;
import jdk.internal.classfile.*;
import jdk.internal.classfile.attribute.MatcherAttribute;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static helpers.ClassRecord.assertEqualsDeep;
import static helpers.TestUtil.assertEmpty;
import static org.junit.jupiter.api.Assertions.*;

import jdk.internal.classfile.components.ClassPrinter;
import org.junit.jupiter.api.Test;

class MatcherTest {

    @Test
    void testReadDeconstructor() throws Exception {
        List<String> extractedInfo = new ArrayList<>();
        Classfile cc = Classfile.of();
        ClassTransform xform = (clb, cle) -> {
            if (cle instanceof MethodModel mm) {
                clb.transformMethod(mm, (mb, me) -> {
                    if (me instanceof MatcherAttribute ma) {
                        extractedInfo.add(ma.matcherName().toString());
                        extractedInfo.add(String.valueOf(ma.matcherFlags().contains(AccessFlag.DECONSTRUCTOR)));
                        extractedInfo.add(ma.matcherTypeSymbol().toString());
                        extractedInfo.add(ma.attributes().toString());
                        mb.with(me);
                    } else {
                        mb.with(me);
                    }
                });
            }
            else
                clb.with(cle);
        };
        cc.transform(cc.parse(MatcherTest.class.getResourceAsStream("/testdata/Points.class").readAllBytes()), xform);
        assertEquals(extractedInfo.toString(), "[Points, true, MethodTypeDesc[(Collection,Collection)void], [Attribute[name=MethodParameters], Attribute[name=Signature], Attribute[name=RuntimeVisibleParameterAnnotations]]]");
    }

    @Test
    void testReadAndVerifyDeconstructor() throws IOException {
        Classfile cc = Classfile.of();

        ClassModel classModel = cc.parse(MatcherTest.class.getResourceAsStream("/testdata/Points.class").readAllBytes());

        var verres = classModel.verify(null);

        if (!verres.isEmpty()) {
            ClassPrinter.toYaml(classModel, ClassPrinter.Verbosity.TRACE_ALL, System.out::print);
            assertEmpty(verres);
        }
    }

    private static void assertOut(StringBuilder out, String expected) {
//        System.out.println("-----------------");
//        System.out.println(out.toString());
//        System.out.println("-----------------");
        assertArrayEquals(out.toString().trim().split(" *\r?\n"), expected.trim().split("\n"));
    }
}
