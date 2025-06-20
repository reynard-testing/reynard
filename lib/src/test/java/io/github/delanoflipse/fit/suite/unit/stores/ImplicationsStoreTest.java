package io.github.delanoflipse.fit.suite.unit.stores;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.github.delanoflipse.fit.suite.faultload.Behaviour;
import io.github.delanoflipse.fit.suite.faultload.Fault;
import io.github.delanoflipse.fit.suite.faultload.FaultUid;
import io.github.delanoflipse.fit.suite.faultload.modes.FailureMode;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsModel;
import io.github.delanoflipse.fit.suite.strategy.store.ImplicationsStore;
import io.github.delanoflipse.fit.suite.util.EventBuilder;
import io.github.delanoflipse.fit.suite.util.FailureModes;

public class ImplicationsStoreTest {
  private FailureMode mode1 = FailureModes.getMode(0);
  private FailureMode mode2 = FailureModes.getMode(1);

  ImplicationsStore store;

  EventBuilder nodeA;
  EventBuilder nodeB_alt;
  EventBuilder nodeB;
  EventBuilder nodeC;
  EventBuilder nodeD;
  EventBuilder nodeE;
  EventBuilder nodeF;
  EventBuilder nodeG;
  EventBuilder nodeF_retry;
  EventBuilder nodeG_F_retry;
  EventBuilder nodeF_retry2;
  EventBuilder nodeG_F_retry2;

  Behaviour a;
  Behaviour b;
  Behaviour bprime;
  Behaviour c;
  Behaviour d;
  Behaviour e;
  Behaviour f;
  Behaviour g;
  Behaviour f2;
  Behaviour g2;
  Behaviour f3;
  Behaviour g3;

  @BeforeEach
  public void setUp() {
    nodeA = new EventBuilder().withPoint("A");
    nodeB = nodeA.createChild().withPoint("B");
    nodeC = nodeA.createChild().withPoint("C");
    nodeD = nodeC.createChild().withPoint("D");
    nodeE = nodeC.createChild().withPoint("E");
    nodeF = nodeA.createChild().withPoint("F");
    nodeG = nodeF.createChild().withPoint("G");

    a = nodeA.behaviour();
    b = nodeB.behaviour();
    c = nodeC.behaviour();
    d = nodeD.behaviour();
    e = nodeE.behaviour();
    f = nodeF.behaviour();
    g = nodeG.behaviour();

    store = new ImplicationsStore();

    // happy path
    store.addDownstreamRequests(a.uid(), Set.of(b.uid(), c.uid(), f.uid()));
    store.addDownstreamRequests(c.uid(), Set.of(d.uid(), e.uid()));
    store.addDownstreamRequests(f.uid(), Set.of(g.uid()));
  }

  private void setupUpstreamResponses() {
    // any fault in D/E causes C to be faulty in mode 1
    Behaviour fd1 = new Behaviour(d.uid(), mode1);
    Behaviour fd2 = new Behaviour(d.uid(), mode2);
    Behaviour fe1 = new Behaviour(e.uid(), mode1);
    Behaviour fe2 = new Behaviour(e.uid(), mode2);
    Behaviour fc1 = new Behaviour(c.uid(), mode1);

    store.addUpstreamResponse(Set.of(fd1, e), fc1);
    store.addUpstreamResponse(Set.of(fd2, e), fc1);
    store.addUpstreamResponse(Set.of(d, fe1), fc1);
    store.addUpstreamResponse(Set.of(d, fe2), fc1);
    store.addUpstreamResponse(Set.of(fd1, fe1), fc1);
    store.addUpstreamResponse(Set.of(fd1, fe2), fc1);
    store.addUpstreamResponse(Set.of(fd2, fe1), fc1);
    store.addUpstreamResponse(Set.of(fd2, fe2), fc1);

    // G has no effect downstream (F)
  }

  private void setupExclusion() {
    // B before c and e
    Behaviour fb1 = new Behaviour(b.uid(), mode1);
    Behaviour fb2 = new Behaviour(b.uid(), mode2);
    store.addExclusionEffect(Set.of(fb1), c.uid());
    store.addExclusionEffect(Set.of(fb1), f.uid());
    store.addExclusionEffect(Set.of(fb2), c.uid());
    store.addExclusionEffect(Set.of(fb2), f.uid());

    // C before f
    Behaviour fc1 = new Behaviour(c.uid(), mode1);
    Behaviour fc2 = new Behaviour(c.uid(), mode2);
    store.addExclusionEffect(Set.of(fc1), f.uid());
    store.addExclusionEffect(Set.of(fc2), f.uid());
  }

  private void setupInclusionAndExclusion() {
    // retry on F
    nodeF_retry = nodeA.createChild()
        .withPoint("F", 1);
    f2 = nodeF_retry.behaviour();
    nodeG_F_retry = nodeF_retry.createChild()
        .withPoint("G");
    g2 = nodeG_F_retry.behaviour();
    Behaviour ff1 = new Behaviour(f.uid(), mode1);
    Behaviour ff2 = new Behaviour(f.uid(), mode2);
    store.addDownstreamRequests(f2.uid(), Set.of(g2.uid()));
    store.addInclusionEffect(Set.of(ff1), f2.uid());
    store.addInclusionEffect(Set.of(ff2), f2.uid());

    // retry 2 on F
    nodeF_retry2 = nodeA.createChild()
        .withPoint("F", Map.of(), 2);
    f3 = nodeF_retry2.behaviour();
    nodeG_F_retry2 = nodeF_retry2.createChild()
        .withPoint("G");
    g3 = nodeG_F_retry2.behaviour();
    Behaviour f21 = new Behaviour(f2.uid(), mode1);
    Behaviour f22 = new Behaviour(f2.uid(), mode2);
    store.addDownstreamRequests(f3.uid(), Set.of(g3.uid()));
    store.addInclusionEffect(Set.of(f21), f3.uid());
    store.addInclusionEffect(Set.of(f22), f3.uid());

    // alternative for B, for mode 1
    nodeB_alt = nodeA.createChild()
        .withPoint("B_prime", b.uid().signature(), 0);
    bprime = nodeB_alt.behaviour();
    Behaviour fb = new Behaviour(b.uid(), mode1);
    store.addInclusionEffect(Set.of(fb), bprime.uid());

    // B and B' before c and e
    Behaviour fb1 = new Behaviour(b.uid(), mode1);
    Behaviour fb2 = new Behaviour(b.uid(), mode2);
    Behaviour fbp1 = new Behaviour(bprime.uid(), mode1);
    Behaviour fbp2 = new Behaviour(bprime.uid(), mode2);
    store.addExclusionEffect(Set.of(fb1, fbp1), c.uid());
    store.addExclusionEffect(Set.of(fb1, fbp1), f.uid());
    store.addExclusionEffect(Set.of(fb1, fbp2), c.uid());
    store.addExclusionEffect(Set.of(fb1, fbp2), f.uid());
    store.addExclusionEffect(Set.of(fb2, fbp2), c.uid());
    store.addExclusionEffect(Set.of(fb2, fbp2), c.uid());
    store.addExclusionEffect(Set.of(fb2, fbp1), f.uid());
    store.addExclusionEffect(Set.of(fb2, fbp1), f.uid());

    // C before f (and thus f2)
    Behaviour fc1 = new Behaviour(c.uid(), mode1);
    Behaviour fc2 = new Behaviour(c.uid(), mode2);
    store.addExclusionEffect(Set.of(fc1), f.uid());
    store.addExclusionEffect(Set.of(fc2), f.uid());
  }

  private Set<Behaviour> getFaultyBehaviours(Set<Behaviour> s) {
    return s.stream().filter(b -> b.isFault()).collect(Collectors.toSet());
  }

  private int faultyBehaviours(Set<Behaviour> s) {
    return getFaultyBehaviours(s).size();
  }

  private Set<Behaviour> getExpected(Collection<Fault> faults) {
    return new ImplicationsModel(store).getBehaviours(faults);
  }

  @Test
  public void testUpstream() {
    Set<Behaviour> result = getExpected(Set.of());
    assertEquals(7, result.size());
    assertEquals(0, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtA() {
    Set<Behaviour> result = getExpected(Set.of(
        new Fault(a.uid(), mode1)));
    assertEquals(1, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtB() {
    Set<Behaviour> result = getExpected(Set.of(new Fault(b.uid(), mode1)));
    assertEquals(7, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtC() {
    Set<Behaviour> result = getExpected(Set.of(new Fault(c.uid(), mode1)));
    assertEquals(7 - 2, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtF() {
    Set<Behaviour> result = getExpected(Set.of(new Fault(f.uid(), mode1)));
    assertEquals(7 - 1, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testHideB() {
    setupExclusion();

    Set<Behaviour> result = getExpected(Set.of(new Fault(b.uid(), mode1)));
    // A and B, which hides C and F (and others)
    assertEquals(2, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testHideC() {
    setupExclusion();

    Set<Behaviour> result = getExpected(Set.of(new Fault(c.uid(), mode1)));
    assertEquals(3, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamD() {
    setupUpstreamResponses();

    Set<Behaviour> result = getExpected(Set.of(new Fault(d.uid(), mode1)));
    Set<Behaviour> faulty = getFaultyBehaviours(result);
    assertEquals(2, faulty.size());
  }

  @Test
  public void testDownstreamE() {
    setupUpstreamResponses();

    Set<Behaviour> result = getExpected(Set.of(new Fault(e.uid(), mode1)));
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamG() {
    setupUpstreamResponses();

    Set<Behaviour> result = getExpected(Set.of(new Fault(g.uid(), mode1)));
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamDE() {
    setupUpstreamResponses();

    Set<Behaviour> result = getExpected(Set.of(new Fault(d.uid(), mode1), new Fault(e.uid(), mode2)));
    assertEquals(3, faultyBehaviours(result));
  }

  @Test
  public void testHideDDownstreamHideF() {
    setupUpstreamResponses();
    setupExclusion();

    Set<Behaviour> result = getExpected(Set.of(new Fault(d.uid(), mode1)));
    assertEquals(5, result.size());
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testAppearBprime() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(Set.of(new Fault(b.uid(), mode1)));
    assertEquals(8, result.size());
  }

  @Test
  public void testNotAppearBprime() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(Set.of(new Fault(b.uid(), mode2)));
    assertEquals(7, result.size());
  }

  @Test
  public void testAppearF2() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(Set.of(new Fault(f.uid(), mode1)));
    assertEquals(8, result.size());
  }

  @Test
  public void testAppearBoth() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(Set.of(new Fault(b.uid(), mode1), new Fault(f.uid(), mode1)));
    assertEquals(9, result.size());
  }

  @Test
  public void testAppearBHideCF() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(
        Set.of(new Fault(b.uid(), mode1), new Fault(bprime.uid(), mode1)));

    // only A, b and b', which hides C and F
    assertEquals(3, result.size());
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testComplex() {
    setupUpstreamResponses();
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(
        Set.of(new Fault(b.uid(), mode1), new Fault(d.uid(), mode1)));

    // A, b, b', c, d, e; exludes all f(0-2), g's
    assertEquals(6, result.size());
    assertEquals(3, faultyBehaviours(result));
  }

  @Test
  public void testComplexRedundant() {
    setupUpstreamResponses();
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(
        Set.of(new Fault(f.uid(), mode1), new Fault(d.uid(), mode1)));

    // A, b, b', c, d, e; exludes f, g, and thus f2
    // (f is redundant)
    assertEquals(5, result.size());
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testComplexRedundant2() {
    setupUpstreamResponses();
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(
        Set.of(new Fault(b.uid(), mode1), new Fault(bprime.uid(), mode1), new Fault(e.uid(), mode1)));

    // A, b, b'; exludes c, d, e f, g
    // (e is redundant)
    assertEquals(3, result.size());
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testPersistent() {
    setupUpstreamResponses();
    setupInclusionAndExclusion();

    Set<Behaviour> result = getExpected(Set.of(new Fault(f.uid().asAnyCount(), mode1)));

    // a, b, c, d, e, f, f2, f3
    assertEquals(8, result.size());
    assertEquals(3, faultyBehaviours(result));
  }

  @Test
  public void testInclusionExclusionEdgeCase() {
    ImplicationsStore testStore = new ImplicationsStore();

    var nRoot = new EventBuilder()
        .withPoint("R", "r1");

    var nA = nRoot.createChild()
        .withPoint("A", "a1");

    var nB = nRoot.createChild()
        .withPoint("B", "b1");

    var nAprime = nRoot.createChild()
        .withPoint("A prime", "a1");

    var nBprime = nRoot.createChild()
        .withPoint("B prime", "b1");

    // Happy path, root calls A and B
    testStore.addDownstreamRequests(nRoot.uid(), Set.of(
        nA.uid(), nB.uid()));

    // If A or B is faulty, then A' and B' are called
    testStore.addInclusionEffect(Set.of(
        new Behaviour(nA.uid(), mode1)), nAprime.uid());

    testStore.addInclusionEffect(Set.of(
        new Behaviour(nA.uid(), mode1)), nBprime.uid());

    testStore.addInclusionEffect(Set.of(
        new Behaviour(nB.uid(), mode1)), nAprime.uid());

    testStore.addInclusionEffect(Set.of(
        new Behaviour(nB.uid(), mode1)), nBprime.uid());

    // If A' is faulty, then B' is not called
    testStore.addExclusionEffect(Set.of(
        new Behaviour(nAprime.uid(), mode1)), nBprime.uid());

    Set<Behaviour> result = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(nA.uid(), mode1),
        new Fault(nB.uid(), mode1),
        new Fault(nAprime.uid(), mode1)));

    // Expect root, A, B, A' but not B'
    assertEquals(4, result.size());
    assertEquals(3, faultyBehaviours(result));
  }

  @Test
  public void testTwoInclusions() {
    ImplicationsStore testStore = new ImplicationsStore();

    var nRoot = new EventBuilder()
        .withPoint("R", "r1");

    var nA = nRoot.createChild()
        .withPoint("A", "a1");

    var nAfallback1 = nRoot.createChild()
        .withPoint("A fallback 1", "x1");

    var nAfallback2 = nRoot.createChild()
        .withPoint("A fallback 2", "y1");

    // Happy path, root calls A and B
    testStore.addDownstreamRequests(nRoot.uid(), Set.of(nA.uid()));

    // If A is faulty, then X and Y are called
    testStore.addInclusionEffect(Set.of(
        new Behaviour(nA.uid(), mode1)), nAfallback1.uid());

    testStore.addInclusionEffect(Set.of(
        new Behaviour(nA.uid(), mode1)), nAfallback2.uid());

    Set<Behaviour> result = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(nA.uid(), mode1),
        new Fault(nAfallback2.uid(), mode1)));

    // Expect root, A, X and Y
    assertEquals(4, result.size());
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  @Disabled("This test illustrates an issue when not using call stacks")
  public void testContradiction() {
    ImplicationsStore testStore = new ImplicationsStore();

    var nRoot = new EventBuilder().withPoint("R");
    var r = nRoot.uid();

    var nA = nRoot.createChild().withPoint("A");
    var a = nA.uid();

    var nB = nRoot.createChild().withPoint("B");
    var b = nB.uid();

    var nC = nRoot.createChild().withPoint("C");
    var c = nC.uid();

    var nD = nRoot.createChild().withPoint("D");
    var d = nD.uid();

    // Happy path, root calls A B, D
    testStore.addDownstreamRequests(r, Set.of(a, b, d));

    // Fallback for A is C
    testStore.addInclusionEffect(Set.of(new Behaviour(a, mode1)), c);
    // fallback for B is C
    testStore.addInclusionEffect(Set.of(new Behaviour(b, mode1)), c);

    // if A&C fail, then no b or d
    testStore.addExclusionEffect(Set.of(new Behaviour(a, mode1), new Behaviour(c, mode1)), b);
    testStore.addExclusionEffect(Set.of(new Behaviour(a, mode1), new Behaviour(c, mode1)), d);

    // if B&C fail, then no d
    testStore.addExclusionEffect(Set.of(new Behaviour(b, mode1), new Behaviour(c, mode1)), d);

    // if A, B and C fail, then no D
    // But which c are we failing?
    Set<Behaviour> res4 = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(a, mode1),
        new Fault(b, mode1),
        new Fault(c, mode1)));

    // This fails, because these are different C's, but they have the same id
    assertEquals(4, res4.size());
    assertEquals(2, faultyBehaviours(res4));
  }

  @Test
  public void testContradiction2() {
    ImplicationsStore testStore = new ImplicationsStore();

    var nRoot = new EventBuilder()
        .withPoint("R", "r1");
    var r = nRoot.uid();

    var nA = nRoot.createChild()
        .withPoint("A", "a1");
    var a = nA.uid();

    var nC1 = nRoot.createChild()
        .withPoint("C", "c1", Map.of("A", 1));
    var c1 = nC1.uid();

    var nBafterA = nRoot.createChild()
        .withPoint("B", "b1", Map.of("A", 1));
    var b_a = nBafterA.uid();

    var nBafterC = nRoot.createChild()
        .withPoint("B", "b1", Map.of("A", 1, "C", 1));
    var b_ac = nBafterC.uid();

    var nC2 = nRoot.createChild()
        .withPoint("C", "c1", Map.of("A", 1, "B", 1), 0);
    FaultUid c2 = nC2.uid();

    var nD = nRoot.createChild()
        .withPoint("D", "d1");
    var d = nD.uid();

    // Happy path, root calls A and B
    testStore.addDownstreamRequests(r, Set.of(a, b_a, d));

    // Fallback for A is C1, causing B to be called in a different manner
    testStore.addInclusionEffect(Set.of(new Behaviour(a, mode1)), c1);
    testStore.addExclusionEffect(Set.of(new Behaviour(a, mode1)), b_a);
    testStore.addInclusionEffect(Set.of(new Behaviour(a, mode1)), b_ac);

    // fallback for B is C2
    testStore.addInclusionEffect(Set.of(new Behaviour(b_a, mode1)), c2);

    // if A&C1 fail, then no b or d
    testStore.addExclusionEffect(Set.of(new Behaviour(a, mode1), new Behaviour(c1, mode1)), b_ac);

    testStore.addExclusionEffect(Set.of(new Behaviour(a, mode1), new Behaviour(c1, mode1)), d);

    // if B&C2 fail, then no d
    testStore.addExclusionEffect(Set.of(new Behaviour(b_a, mode1), new Behaviour(c2, mode1)), d);
    testStore.addExclusionEffect(Set.of(new Behaviour(b_ac, mode1), new Behaviour(c2, mode1)), d);

    // If A and B fail
    Set<Behaviour> res1 = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(a, mode1),
        new Fault(b_ac, mode1)));

    // Expect A, C1, B after C1, D
    assertEquals(5, res1.size());
    assertEquals(2, faultyBehaviours(res1));

    // if A and C fail, then no B or D
    Set<Behaviour> res2 = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(a, mode1),
        new Fault(c1, mode1)));

    assertEquals(3, res2.size());
    assertEquals(2, faultyBehaviours(res2));

    // if B and C fail, then no D
    Set<Behaviour> res3 = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(b_a, mode1),
        new Fault(c2, mode1)));

    assertEquals(4, res3.size());
    assertEquals(2, faultyBehaviours(res3));

    // if A, B and C1 doesnt fail, then no C2
    Set<Behaviour> res4 = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(a, mode1),
        new Fault(b_ac, mode1),
        new Fault(c2, mode1)));

    // We expect to see, A, C1, B, D
    // But not C2, because it is excluded
    assertEquals(5, res4.size());
    assertEquals(2, faultyBehaviours(res4));

    // if A, and C1 fail, then no B or D
    var f5 = Set.of(
        new Fault(a, mode1),
        new Fault(b_ac, mode1),
        new Fault(c1, mode1));
    Set<Behaviour> res5 = new ImplicationsModel(testStore).getBehaviours(f5);

    // We expect to see, A, C1
    assertEquals(3, res5.size());
    assertEquals(2, faultyBehaviours(res5));
  }

  @Test
  public void testComplexInclusionsAndExclusions() {
    ImplicationsStore testStore = new ImplicationsStore();

    var nRoot = new EventBuilder().withPoint("R");
    var r = nRoot.uid();

    var nA = nRoot.createChild().withPoint("A");
    var a = nA.uid();

    // A1 = !A
    var nA1 = nRoot.createChild().withPoint("A", 1);
    var a1 = nA1.uid();

    var nB = nRoot.createChild().withPoint("B");
    var b = nB.uid();

    // B1 = !B
    var nB1 = nRoot.createChild().withPoint("B", 1);
    var b1 = nB1.uid();

    // C = !A & !B
    var nC = nRoot.createChild().withPoint("C");
    var c = nC.uid();

    // D = !A | !B
    var nD = nRoot.createChild().withPoint("D");
    var d = nD.uid();

    // E = !A XOR !B
    var nE = nRoot.createChild().withPoint("E");
    var e = nE.uid();

    // Happy path, root calls A and B
    testStore.addDownstreamRequests(r, Set.of(a, b));

    // Retry for A1 on fail of a
    testStore.addInclusionEffect(Set.of(new Behaviour(a, mode1)), a1);
    testStore.addInclusionEffect(Set.of(new Behaviour(b, mode1)), b1);

    // If A or B fails, send to C
    testStore.addInclusionEffect(Set.of(new Behaviour(a, mode1)), d);
    testStore.addInclusionEffect(Set.of(new Behaviour(b, mode1)), d);

    testStore.addInclusionEffect(Set.of(new Behaviour(a, mode1)), e);
    testStore.addInclusionEffect(Set.of(new Behaviour(b, mode1)), e);

    // If A and B fail, include C
    testStore.addInclusionEffect(Set.of(new Behaviour(a, mode1), new Behaviour(b, mode1)), c);

    // if A&A1, exclude b1 (and thus b2)
    testStore.addExclusionEffect(Set.of(new Behaviour(a, mode1), new Behaviour(a1, mode1)), b);

    // if A and B fail, do not include E
    testStore.addExclusionEffect(Set.of(new Behaviour(a, mode1), new Behaviour(b, mode1)), e);

    // If A and B fail
    Set<Behaviour> res1 = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(a, mode1),
        new Fault(b, mode1)));

    // Expect R, A, A1, B, B1, C, D, but not E
    assertEquals(7, res1.size());
    assertEquals(2, faultyBehaviours(res1));

    // if A and A1 fail, exclude B, B1 and C, but include D, E
    Set<Behaviour> res2 = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(a, mode1),
        new Fault(a1, mode1)));

    assertEquals(5, res2.size());
    assertEquals(2, faultyBehaviours(res2));

    // if A and A1 fail, exclude B and,B1, C, but include D, E
    Set<Behaviour> res3 = new ImplicationsModel(testStore).getBehaviours(Set.of(
        new Fault(a, mode1),
        new Fault(b, mode1),
        new Fault(a1, mode1)));

    assertEquals(5, res3.size());
    assertEquals(2, faultyBehaviours(res3));
  }

  @Test
  public void stressTest() {
    ImplicationsStore testStore = new ImplicationsStore();
    final int childrenCount = 1000;
    var root = new EventBuilder().withPoint("R");
    var r = root.uid();

    var children = new ArrayList<EventBuilder>();
    for (int i = 0; i < childrenCount; i++) {
      var child = root.createChild().withPoint("N" + i);
      var subChild = child.createChild().withPoint("S" + i);
      testStore.addDownstreamRequests(child.uid(), List.of(subChild.uid()));
      children.add(child);
    }

    // Happy path, include first child
    testStore.addDownstreamRequests(r, List.of(children.get(0).uid()));
    for (int i = 1; i < childrenCount; i++) {
      // Each child includes the next one
      testStore.addInclusionEffect(Set.of(new Behaviour(children.get(i - 1).uid(), mode1)), children.get(i).uid());
    }

    List<Fault> faults = new ArrayList<>();
    for (int i = 0; i < childrenCount - 1; i++) {
      faults.add(new Fault(children.get(i).uid(), mode1));
    }

    Set<Behaviour> res = new ImplicationsModel(testStore).getBehaviours(faults);

    assertEquals(1 + childrenCount + 1, res.size());
  }
}
