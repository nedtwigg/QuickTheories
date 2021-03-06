package org.quicktheories.quicktheories.impl;

import static org.quicktheories.quicktheories.impl.Util.equaliseShrinkLength;
import static org.quicktheories.quicktheories.impl.Util.zip;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.CheckReturnValue;

import org.quicktheories.quicktheories.api.AsString;
import org.quicktheories.quicktheories.api.Function3;
import org.quicktheories.quicktheories.api.Pair;
import org.quicktheories.quicktheories.api.Predicate3;
import org.quicktheories.quicktheories.api.Subject1;
import org.quicktheories.quicktheories.api.Subject4;
import org.quicktheories.quicktheories.api.TriConsumer;
import org.quicktheories.quicktheories.api.Tuple3;
import org.quicktheories.quicktheories.api.Tuple4;
import org.quicktheories.quicktheories.core.Generator;
import org.quicktheories.quicktheories.core.Shrink;
import org.quicktheories.quicktheories.core.Source;
import org.quicktheories.quicktheories.core.Strategy;

public final class TheoryBuilder3<A, B, C> {
  private final Supplier<Strategy> state;
  private final Source<A> as;
  private final Source<B> bs;
  private final Source<C> cs;
  private final Predicate3<A, B, C> assumptions;

  /**
   * Builds theories about values of type A and B
   *
   * @param state
   *          supplies the strategy to be implemented
   * @param as
   *          the first source of the values to be generated and potentially
   *          shrunk
   * @param bs
   *          the second source of the values to be generated and potentially
   *          shrunk
   * @param assumptions
   *          limits the possible values of type A and of type B
   */

  /**
   * Builds theories about values of type A, B and C
   *
   * @param state
   *          supplies the strategy to be implemented
   * @param as
   *          the first source of the values to be generated and potentially
   *          shrunk
   * @param bs
   *          the second source of the values to be generated and potentially
   *          shrunk
   * @param cs
   *          the third source of the values to be generated and potentially
   *          shrunk
   * @param assumptions
   *          limits the possible values of type A, type B and type C
   */
  public TheoryBuilder3(final Supplier<Strategy> state, final Source<A> as,
      Source<B> bs, Source<C> cs, Predicate3<A, B, C> assumptions) {
    this.state = state;
    this.as = as;
    this.bs = bs;
    this.cs = cs;
    this.assumptions = assumptions;
  }

  /**
   * Constrains the values a theory must be true for by the given assumption
   *
   * @param newAssumption
   *          an assumption that must be true of all values
   * @return theory builder based on the given assumption
   */
  @CheckReturnValue
  public TheoryBuilder3<A, B, C> assuming(Predicate3<A, B, C> newAssumption) {
    return new TheoryBuilder3<A, B, C>(this.state, this.as, this.bs, this.cs,
        this.assumptions.and(newAssumption));
  }

  /**
   * Converts theory to one about a different type using the given function
   *
   * @param <T>
   *          type to convert to
   * @param mapping
   *          function with which to map values to desired type
   * @return theory builder about type T
   */
  @CheckReturnValue
  public <T> Subject1<T> as(
      Function3<A, B, C, T> mapping) {
    return new MappingTheoryBuilder<>(this.state, combine(),
        precursor -> this.assumptions.test(precursor._1, precursor._2,
            precursor._3),
        tuple -> mapping.apply(tuple._1, tuple._2,
            tuple._3),
        t -> t.toString());
  }

  /**
   * Converts theory to one about a different type using the given function
   * retaining all precursor values
   *
   * @param <T>
   *          type to create theory about
   *
   * @param mapping
   *          Function from types A,B,C to type T
   * @return a Subject4 relating to the state of a theory involving four values
   */
  @CheckReturnValue
  public <T> Subject4<A, B, C, T> asWithPrecursor(
      Function3<A, B, C, T> mapping) {
    return this.asWithPrecursor(mapping, t -> t.toString());
  }

  /**
   * Converts theory to one about a different type using the given function
   * retaining all precursor values
   *
   * @param <T>
   *          type to create theory about
   *
   * @param mapping
   *          Function from types A,B,C to type T
   * @param typeToString
   *          Function to describe generated type
   * @return a Subject4 relating to the state of a theory involving four values
   */
  @CheckReturnValue
  public <T> Subject4<A, B, C, T> asWithPrecursor(
      Function3<A, B, C, T> mapping, Function<T, String> typeToString) {
    final Shrink<Tuple4<A, B, C, T>> shrink = (original,
        context) -> combineShrinks().shrink(
            Tuple3.of(original._1, original._2, original._3), context)
            .map(precursor -> precursor.extend(mapping));

    final AsString<Tuple4<A, B, C, T>> desc = tuple -> tuple
        .map(this.as.asToStringFunction(), this.bs.asToStringFunction(),
            this.cs.asToStringFunction(),
            typeToString)
        .toString();

    final Source<Tuple4<A, B, C, T>> gen = Source
        .of(generatePrecursorValueTuple(mapping)).withShrinker(shrink)
        .describedAs(desc);
    return new PrecursorTheoryBuilder3<A, B, C, T>(this.state, gen,
        this.assumptions);
  }

  /**
   * Checks a boolean property across a random sample of possible values
   *
   * @param property
   *          property to check
   */
  public void check(final Predicate3<A, B, C> property) {
    final TheoryRunner<Tuple3<A, B, C>, Tuple3<A, B, C>> qc = TheoryRunner
        .runner(
            this.state.get(),
            combine(), convertPredicate());
    qc.check(x -> property.test(x._1, x._2, x._3));
  }

  /**
   * Checks a property across a random sample of possible values where
   * falsification is indicated by an unchecked exception such as an assertion
   *
   * @param property
   *          property to check
   */
  public final void checkAssert(final TriConsumer<A, B, C> property) {
    check((a, b, c) -> {
      property.accept(a, b, c);
      return true;
    });
  }

  private <T> Generator<Tuple4<A, B, C, T>> generatePrecursorValueTuple(
      Function3<A, B, C, T> mapping) {
    return prgnToTuple().andThen(precursor -> precursor.extend(mapping));
  }

  private Predicate<Tuple3<A, B, C>> convertPredicate() {
    return tuple -> this.assumptions.test(tuple._1, tuple._2,
        tuple._3);
  }

  private Source<Tuple3<A, B, C>> combine() {
    return Source.of(prgnToTuple()).withShrinker(combineShrinks())
        .describedAs(joinToString());
  }

  private Shrink<Tuple3<A, B, C>> combineShrinks() {
    return (tuple, context) -> {
      final Stream<A> equalLengthedSteamOfA = equaliseShrinkLength(this.as,
          () -> tuple._1,
          context);
      final Stream<B> equalLengthedSteamOfB = equaliseShrinkLength(this.bs,
          () -> tuple._2,
          context);
      final Stream<C> equalLengthedSteamOfC = equaliseShrinkLength(this.cs,
          () -> tuple._3,
          context);

      final Stream<Pair<B, C>> bcStream = zip(equalLengthedSteamOfB,
          equalLengthedSteamOfC, (b, c) -> Pair.of(b, c));

      return zip(
          bcStream, equalLengthedSteamOfA, (bc, a) -> bc.prepend(a));

    };
  }

  private Generator<Tuple3<A, B, C>> prgnToTuple() {
    return (prng, step) -> Tuple3.of(this.as.next(prng, step),
        this.bs.next(prng, step), this.cs.next(prng, step));
  }

  private AsString<Tuple3<A, B, C>> joinToString() {
    return tuple -> tuple.map(this.as.asToStringFunction(),
        this.bs.asToStringFunction(), this.cs.asToStringFunction()).toString();
  }

}