/*
 *
 *  Copyright Datadobi
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software

 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.datadobi.s3test.s3;

import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import static com.datadobi.s3test.s3.S3TestBase.ignoreDueToQuirk;

public class SkipForQuirksRule implements MethodRule {
    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        SkipForQuirks skipForQuirks = method.getAnnotation(SkipForQuirks.class);
        if (skipForQuirks == null) {
            return base;
        }

        if (target instanceof S3TestBase s) {
            for (var q : skipForQuirks.value()) {
                if (s.target.hasQuirk(q)) {
                    return new IgnoreStatement(base, q);
                }
            }
        }

        return base;
    }

    private static class IgnoreStatement extends Statement {
        private final Statement next;
        private final Quirk quirk;

        private IgnoreStatement(Statement next, Quirk quirk) {
            this.next = next;
            this.quirk = quirk;
        }

        @Override
        public void evaluate() throws Throwable {
            ignoreDueToQuirk(quirk);
            this.next.evaluate();
        }
    }
}
