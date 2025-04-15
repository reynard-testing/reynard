package nl.dflipse.fit.stores;

import static org.junit.Assert.assertEquals;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.dflipse.fit.faultload.Behaviour;
import nl.dflipse.fit.faultload.Fault;
import nl.dflipse.fit.faultload.modes.FailureMode;
import nl.dflipse.fit.strategy.store.ImplicationsStore;
import nl.dflipse.fit.util.EventBuilder;
import nl.dflipse.fit.util.FailureModes;

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

  @BeforeEach
  public void setUp() {
    nodeA = new EventBuilder()
        .withPoint("A", "a1");

    nodeB = nodeA.createChild()
        .withPoint("B", "b1");

    nodeC = nodeA.createChild()
        .withPoint("C", "c1");

    nodeD = nodeC.createChild()
        .withPoint("D", "d1");

    nodeE = nodeC.createChild()
        .withPoint("E", "e1");

    nodeF = nodeA.createChild()
        .withPoint("F", "f1");

    nodeG = nodeF.createChild()
        .withPoint("G", "g1");

    a = nodeA.getBehaviour();
    b = nodeB.getBehaviour();
    c = nodeC.getBehaviour();
    d = nodeD.getBehaviour();
    e = nodeE.getBehaviour();
    f = nodeF.getBehaviour();
    g = nodeG.getBehaviour();

    store = new ImplicationsStore();

    // happy path
    store.addUpstreamEffect(a.uid(), Set.of(b.uid(), c.uid(), f.uid()));
    store.addUpstreamEffect(c.uid(), Set.of(d.uid(), e.uid()));
    store.addUpstreamEffect(f.uid(), Set.of(g.uid()));
  }

  private void setupDownstream() {
    // any fault in D/E causes C to be faulty in mode 1
    Behaviour fd1 = new Behaviour(d.uid(), mode1);
    Behaviour fd2 = new Behaviour(d.uid(), mode2);
    Behaviour fe1 = new Behaviour(e.uid(), mode1);
    Behaviour fe2 = new Behaviour(e.uid(), mode2);
    Behaviour fc1 = new Behaviour(c.uid(), mode1);

    store.addDownstreamEffect(Set.of(fd1, e), fc1);
    store.addDownstreamEffect(Set.of(fd2, e), fc1);
    store.addDownstreamEffect(Set.of(d, fe1), fc1);
    store.addDownstreamEffect(Set.of(d, fe2), fc1);
    store.addDownstreamEffect(Set.of(fd1, fe1), fc1);
    store.addDownstreamEffect(Set.of(fd1, fe2), fc1);
    store.addDownstreamEffect(Set.of(fd2, fe1), fc1);
    store.addDownstreamEffect(Set.of(fd2, fe2), fc1);

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
        .withPoint("F", "f1", 1);
    f2 = nodeF_retry.getBehaviour();
    nodeG_F_retry = nodeF_retry.createChild()
        .withPoint("G", "g1");
    g2 = nodeG_F_retry.getBehaviour();
    Behaviour ff1 = new Behaviour(f.uid(), mode1);
    Behaviour ff2 = new Behaviour(f.uid(), mode2);
    store.addUpstreamEffect(f2.uid(), Set.of(g2.uid()));
    store.addInclusionEffect(Set.of(ff1), f2.uid());
    store.addInclusionEffect(Set.of(ff2), f2.uid());

    // alternative for B, for mode 1
    nodeB_alt = nodeA.createChild()
        .withPoint("B_prime", "b1", 0);
    bprime = nodeB_alt.getBehaviour();
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

  @Test
  public void testUpstream() {
    Set<Behaviour> result = store.getBehaviours(Set.of());
    assertEquals(7, result.size());
    assertEquals(0, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtA() {
    Set<Behaviour> result = store.getBehaviours(Set.of(
        new Fault(a.uid(), mode1)));
    assertEquals(1, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtB() {
    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(b.uid(), mode1)));
    assertEquals(7, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtC() {
    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(c.uid(), mode1)));
    assertEquals(7 - 2, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtF() {
    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(f.uid(), mode1)));
    assertEquals(7 - 1, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testHideB() {
    setupExclusion();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(b.uid(), mode1)));
    assertEquals(2, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testHideC() {
    setupExclusion();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(c.uid(), mode1)));
    assertEquals(3, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamD() {
    setupDownstream();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(d.uid(), mode1)));
    Set<Behaviour> faulty = getFaultyBehaviours(result);
    assertEquals(2, faulty.size());
  }

  @Test
  public void testDownstreamE() {
    setupDownstream();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(e.uid(), mode1)));
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamG() {
    setupDownstream();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(g.uid(), mode1)));
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamDE() {
    setupDownstream();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(d.uid(), mode1), new Fault(e.uid(), mode2)));
    assertEquals(3, faultyBehaviours(result));
  }

  @Test
  public void testHideDDownstreamHideF() {
    setupDownstream();
    setupExclusion();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(d.uid(), mode1)));
    assertEquals(5, result.size());
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testAppearBprime() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(b.uid(), mode1)));
    assertEquals(8, result.size());
  }

  @Test
  public void testNotAppearBprime() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(b.uid(), mode2)));
    assertEquals(7, result.size());
  }

  @Test
  public void testAppearF2() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(f.uid(), mode1)));
    assertEquals(8, result.size());
  }

  @Test
  public void testAppearBoth() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = store.getBehaviours(Set.of(new Fault(b.uid(), mode1), new Fault(f.uid(), mode1)));
    assertEquals(9, result.size());
  }

  @Test
  public void testAppearBHideCF() {
    setupInclusionAndExclusion();

    Set<Behaviour> result = store.getBehaviours(
        Set.of(new Fault(b.uid(), mode1), new Fault(bprime.uid(), mode1)));

    // only A, b and b', which hides C and F
    assertEquals(3, result.size());
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testComplex() {
    setupDownstream();
    setupInclusionAndExclusion();

    Set<Behaviour> result = store.getBehaviours(
        Set.of(new Fault(b.uid(), mode1), new Fault(d.uid(), mode1)));

    // A, b, b', c, d, e; exludes f, g
    assertEquals(6, result.size());
    assertEquals(3, faultyBehaviours(result));
  }

  @Test
  public void testComplexRedundant() {
    setupDownstream();
    setupInclusionAndExclusion();

    Set<Behaviour> result = store.getBehaviours(
        Set.of(new Fault(f.uid(), mode1), new Fault(d.uid(), mode1)));

    // A, b, b', c, d, e; exludes f, g, and thus f2
    // (f is redundant)
    assertEquals(5, result.size());
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testComplexRedundant2() {
    setupDownstream();
    setupInclusionAndExclusion();

    Set<Behaviour> result = store.getBehaviours(
        Set.of(new Fault(b.uid(), mode1), new Fault(bprime.uid(), mode1), new Fault(e.uid(), mode1)));

    // A, b, b'; exludes c, d, e f, g
    // (e is redundant)
    assertEquals(3, result.size());
    assertEquals(2, faultyBehaviours(result));
  }
}
