* this is meant to be a draft and can be improved in the future,
* this is a collection of guidelines and rules around and the use of `@since` to keep discussions about the topic consistent.

# `@since` Rules and Guidelines for Java Source Code Documentation

When documenting Java source code, the `@since` tag serves as a crucial indicator of when an element, such as a class, method, field, or nested class, was introduced.

This guide outlines rules and guidelines for the consistent use of the `@since` tag.

Adhering to the following rules ensures clarity and consistency in documenting code changes over different JDK versions. And allows us to programmatically check the accuracy of `@since` in source code documentation.

## Rules

### Rule 1: Introduction of New Elements

- If an element is new in JDK N, with no equivalent in JDK N-1, it must include `@since N`.
  - Exception: Member elements (fields, methods, nested classes) may omit `@since` if their version matches the value specified for the enclosing class or interface.

### Rule 2: Existing Elements in Subsequent JDK Versions

- If an element exists in JDK N, with an equivalent in JDK N-1, it should not include `@since N`.

### Rule 3: Handling Missing `@since` Tags in methods if there is no `@since`

- When inspecting methods, prioritize the `@since` annotation of the supertype's overridden method.
- If unavailable or if the enclosing class's `@since` is newer, use the enclosing element's `@since`.

  I.e. if A extends B, and we add a method to B in JDK N, and add an override of the method to A in JDK M (M > N), we will use N as the effective `@since` for the method.
  Here's an illustration using Markdown and Java code:

```java
/**
 * This class represents the superclass.
 * @since JDK N
 */
public class B {
    /**
     * A method in the superclass.
     * @since JDK N
     */
    public void method() {
        // Some implementation
    }
}

/**
 * This class represents the subclass.
 * @since JDK N
 */
public class A extends B {
    /**
     * An overridden method in the subclass. does not Need `@since`
     */
    @Override
    public void method() {
        // Some implementation
    }
}
```

In this scenario:
- The `B` class is the superclass with a method annotated with `@since JDK N`.
- The `A` class is the subclass and is annotated with `@since JDK N`, it overrides the method from `B` in `JDK M`.
- Since `M > N`, the effective `@since` for the overridden method in class `A` will be `JDK N`, following the guideline described.