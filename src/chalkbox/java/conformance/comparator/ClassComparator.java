package chalkbox.java.conformance.comparator;

import chalkbox.java.conformance.comparator.flags.Flag;
import chalkbox.java.conformance.comparator.flags.ListFlag;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.*;

public class ClassComparator extends CodeComparator<Class> {
    public ClassComparator(Class expected, Class actual) {
        super(expected, actual);
        name = expected.isInterface() ? "interface " : "class ";
        name = name + expected.getName();
    }

    @Override
    protected void compare(Class expected, Class actual) {
        flags = new ArrayList<>();

        compareModifier(expected.getModifiers(), actual.getModifiers());

        try {
            ListFlag<String> fieldFlag = new ListFlag<>("Class fields do not match!");
            compareMembers(expected.getFields(), actual.getFields(), fieldFlag);
            flags.add(fieldFlag);

            ListFlag<String> methodFlag = new ListFlag<>("Class methods do not match!");
            compareMembersOverload(expected.getMethods(), actual.getMethods(), methodFlag);
            //compareMembers(expected.getMethods(), actual.getMethods(), methodFlag);
            flags.add(methodFlag);

            ListFlag<String> constructorFlag = new ListFlag<>("Class constructors do not match!");
            compareMembersOverload(expected.getConstructors(),
                    actual.getConstructors(), constructorFlag);
            flags.add(constructorFlag);
        } catch (NoClassDefFoundError ncd) {
            flags.add(new Flag("Failed to load fields: " + ncd.getMessage(), true));
        }

        Flag shouldBeInterface = new Flag("Expected class to be an interface");
        shouldBeInterface.setFlag(expected.isInterface() && !actual.isInterface());

        Flag shouldBeClass = new Flag("Class should not be an interface");
        shouldBeClass.setFlag(!expected.isInterface() && actual.isInterface());

        flags.add(shouldBeInterface);
        flags.add(shouldBeClass);
    }

    private void compareMembers(Member[] expected, Member[] actual,
                                ListFlag<String> flag) {
        Map<String, Member> expectedMembers = new HashMap<>();

        for (Member member : expected) {
            if (member.getDeclaringClass().getName().startsWith("java.")) {
                continue;
            }
            flag.addExpected(member.getName());
            expectedMembers.put(member.getName(), member);
        }
        for (Member member : actual) {
            if (member.getDeclaringClass().getName().startsWith("java.")) {
                continue;
            }
            flag.addActual(member.getName());
            Member expectedMember = expectedMembers.get(member.getName());
            if (expectedMember != null) {
                CodeComparator comparator = buildComparator(expectedMember, member);
                comparator.indent = indent + 4;
                subComparators.add(comparator);
            }
        }
    }

    private CodeComparator buildComparator(Member expected, Member actual) {
        if ((expected instanceof Field) && (actual instanceof Field)) {
            return new FieldComparator((Field) expected, (Field) actual);
        } else if ((expected instanceof Method) && (actual instanceof Method)) {
            return new MethodComparator((Method) expected, (Method) actual);
        } else if ((expected instanceof Constructor) && (actual instanceof Constructor)) {
            return new ConstructorComparator((Constructor) expected, (Constructor) actual);
        }

        throw new RuntimeException("Unknown comparator types");
    }

    private void compareMembersOverload(Member[] expected, Member[] actual,
                                        ListFlag<String> flag) {
        // Group all methods by name ( collecting overloaded )
        Map<String, List<Member>> expectedMembers = new HashMap<>();
        for (Member member : expected) {
            if (member.getDeclaringClass().getName().startsWith("java.")) {
                continue;
            }
            if (!expectedMembers.containsKey(member.getName())) {
                flag.addExpected(member.getName());
                expectedMembers.put(member.getName(), new ArrayList<>());
            }
            expectedMembers.get(member.getName()).add(member);
        }
        // Group all methods by name ( collecting overloaded )
        Map<String, List<Member>> actualMembers = new HashMap<>();
        for (Member member : actual) {
            if (member.getDeclaringClass().getName().startsWith("java.")) {
                continue;
            }
            if (!actualMembers.containsKey(member.getName())) {
                flag.addActual(member.getName());
                actualMembers.put(member.getName(), new ArrayList<>());
            }
            actualMembers.get(member.getName()).add(member);
        }
        // pair up the actual and expected that match and then attempt to match the rest based on tostring. Use the
        // baselines if we run out of pairs.
        for (var member : actualMembers.keySet()) {
            List<Member> expOverloads = expectedMembers.get(member);
            if (expOverloads == null) {
                continue;
            }
            expOverloads.sort(Comparator.comparing(Member::toString)); // todo: check this works
            List<Member> actOverloads = actualMembers.get(member);
            actOverloads.sort(Comparator.comparing(Member::toString)); // todo: check this works
            List<Member> expOverloadsLeft = new ArrayList<>(expOverloads);
            List<Member> actOverloadsLeft = new ArrayList<>(actOverloads);
            // we know we have atleast 1 of each by this point.
            var baselineExpected = expOverloads.get(0);
            var baselineActual = actOverloads.get(0);
            for (var actOverload : actOverloads) {
                for (var expOverload : expOverloads) {
                    var comparator = buildComparator(expOverload, actOverload);
                    if (!comparator.hasDifference()) {
                        comparator.indent = indent + 4;
                        subComparators.add(comparator);
                        actOverloadsLeft.remove(actOverload);
                        expOverloadsLeft.remove(expOverload);
                    }
                }
            }
            int remainingPos = 0;
            for (remainingPos = 0; remainingPos < Integer.min(expOverloadsLeft.size(), actOverloadsLeft.size()); remainingPos++) {
                var comparator = buildComparator(expOverloadsLeft.get(remainingPos), actOverloadsLeft.get(remainingPos));
                comparator.indent = indent + 4;
                subComparators.add(comparator);
            }
            // Only one of the following will run since we have already reached the max of one of the lists.
            for (; remainingPos < expOverloadsLeft.size(); remainingPos++) {
                var comparator = buildComparator(expOverloadsLeft.get(remainingPos), baselineActual);
                comparator.indent = indent + 4;
                subComparators.add(comparator);
            }
            for (; remainingPos < actOverloadsLeft.size(); remainingPos++) {
                var comparator = buildComparator(baselineExpected, actOverloadsLeft.get(remainingPos));
                comparator.indent = indent + 4;
                subComparators.add(comparator);
            }
        }
    }
}
