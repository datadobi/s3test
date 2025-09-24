package com.datadobi.s3test;

import com.datadobi.s3test.s3.S3TestBase;
import com.datadobi.s3test.s3.ServiceDefinition;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.InitializationError;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RunTests {
    public static void main(String[] args) throws InitializationError, IOException {
        boolean baseTests = true;
        boolean lockingTests = false;
        boolean versioningTests = false;
        String include = null;
        String exclude = null;
        Duration ecDelay = null;

        int i = 0;
        for (; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("-")) {
                break;
            }

            if (arg.equals("-a") || arg.equals("--all-tests")) {
                baseTests = true;
                lockingTests = true;
                versioningTests = true;
            } else if (arg.equals("-b") || arg.equals("--no-base-tests")) {
                baseTests = false;
            } else if (arg.equals("-e") || arg.equals("--exclude")) {
                exclude = args[++i];
            } else if (arg.equals("-i") || arg.equals("--include")) {
                include = args[++i];
            } else if (arg.equals("-l") || arg.equals("--locking-tests")) {
                lockingTests = true;
            } else if (arg.equals("-v") || arg.equals("--versioning-tests")) {
                versioningTests = true;
            } else if (arg.equals("-d") || arg.equals("--eventual-consistency")) {
                ecDelay = Duration.parse(args[++i]);
            }
        }

        if (i == args.length) {
            System.err.println("Usage: RunTests [options] S3_URI");
            System.err.println("Options:");
            System.err.println("  -a --all-tests          Enable all tests");
            System.err.println("  -b --no-base-tests      Disable base tests");
            System.err.println("  -e --exclude PATTERN    Exclude test matching pattern");
            System.err.println("  -i --include PATTERN    Include test matching pattern");
            System.err.println("  -l --locking-tests      Enable locking tests");
            System.err.println("  -s --s3-tests           Enable low-level S3 tests");
            System.err.println("  -v --versioning-tests   Enable versioning tests");
            System.exit(1);
        }

        var target = ServiceDefinition.fromURI(args[i++]);

        if (ecDelay != null) {
            target = target.toBuilder().eventualConsistencyDelay(ecDelay).build();
        }

        S3TestBase.DEFAULT = target;

        System.out.println("S3 tests: " + target.host());
        System.out.println();

        List<Class<?>> classes = new ArrayList<>();

        if (baseTests) {
            classes.add(ChecksumTests.class);
            classes.add(DeleteObjectTests.class);
            classes.add(GetObjectTests.class);
            classes.add(ListObjectsTests.class);
            classes.add(ListBucketsTests.class);
            classes.add(PutObjectTests.class);
            classes.add(MultiPartUploadTests.class);
            classes.add(PrefixDelimiterTests.class);
        }

        if (lockingTests) {
//            classes.add(S3ObjectLockingAssumptionsOnRetentionEnabledBucketTestST.class);
//            classes.add(S3ObjectLockingAssumptionsTestST.class);
        }

        if (versioningTests) {
            // classes.add(S3ObjectVersioningAssumptionsTestST.class);
        }

        JUnitCore junit = new JUnitCore();
        junit.addListener(new TextListener());

        Pattern excludePattern = exclude == null ? null : Pattern.compile(exclude);
        Pattern includePattern = include == null ? null : Pattern.compile(include);

        for (Class<?> c : classes) {
            BlockJUnit4ClassRunner runner = new BlockJUnit4ClassRunner(c);

            try {
                runner.filter(new Filter() {
                    @Override
                    public boolean shouldRun(Description description) {
                        String methodName = description.getMethodName();
                        if (excludePattern != null && excludePattern.matcher(methodName).matches()) {
                            System.out.println(methodName + " excluded");
                            return false;
                        }

                        if (includePattern != null && !includePattern.matcher(methodName).matches()) {
                            System.out.println(methodName + " not included");
                            return false;
                        }

                        return true;
                    }

                    @Override
                    public String describe() {
                        return "Name filter";
                    }
                });
                junit.run(runner);
            } catch (NoTestsRemainException e) {
                System.out.println("Skipping " + runner.getDescription());
            }
        }
    }

    private static class TextListener extends RunListener {
        private final PrintStream stdOut;
        private Failure failure;
        private boolean ignored;

        public TextListener() {
            stdOut = System.out;
        }

        @Override
        public void testRunStarted(Description description) throws Exception {
            stdOut.println("Running " + description);
        }

        public void testStarted(Description description) {
            stdOut.append("  " + description.getMethodName());
            failure = null;
            ignored = false;
        }

        public void testFailure(Failure failure) {
            this.failure = failure;
        }

        public void testIgnored(Description description) {
            ignored = true;
        }

        @Override
        public void testFinished(Description description) throws Exception {
            if (ignored) {
                stdOut.println(" 🙈");
            } else if (failure != null) {
                stdOut.println(" ❌");
                stdOut.println(failure.getTrimmedTrace());
            } else {
                stdOut.println(" ✅");
            }
        }
    }
}
