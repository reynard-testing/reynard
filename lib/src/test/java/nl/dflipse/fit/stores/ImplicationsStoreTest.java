package nl.dflipse.fit.stores;

import static org.junit.Assert.assertEquals;

import java.util.Set;

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
    store.addUpstreamCause(a, Set.of(b, c, f));
    store.addUpstreamCause(c, Set.of(d, e));
    store.addUpstreamCause(f, Set.of(g));
  }

  private void setupDownstream() {
    // any fault in D/E causes C to be faulty in mode 1
    Behaviour fd1 = new Behaviour(d.uid(), mode1);
    Behaviour fd2 = new Behaviour(d.uid(), mode2);
    Behaviour fe1 = new Behaviour(e.uid(), mode1);
    Behaviour fe2 = new Behaviour(e.uid(), mode2);
    Behaviour fc1 = new Behaviour(c.uid(), mode1);

    store.addDownstreamCause(Set.of(fd1, e), fc1);
    store.addDownstreamCause(Set.of(fd2, e), fc1);
    store.addDownstreamCause(Set.of(d, fe1), fc1);
    store.addDownstreamCause(Set.of(d, fe2), fc1);
    store.addDownstreamCause(Set.of(fd1, fe1), fc1);
    store.addDownstreamCause(Set.of(fd1, fe2), fc1);
    store.addDownstreamCause(Set.of(fd2, fe1), fc1);
    store.addDownstreamCause(Set.of(fd2, fe2), fc1);

    // G has no effect downstream (F)
  }

  private void setupExclusion() {
    // B before c and e
    Behaviour fb1 = new Behaviour(b.uid(), mode1);
    Behaviour fb2 = new Behaviour(b.uid(), mode2);
    store.addExclusionEffect(Set.of(fb1), Set.of(c, f));
    store.addExclusionEffect(Set.of(fb2), Set.of(c, f));

    // C before e
    Behaviour fc1 = new Behaviour(c.uid(), mode1);
    Behaviour fc2 = new Behaviour(c.uid(), mode2);
    store.addExclusionEffect(Set.of(fc1), Set.of(f));
    store.addExclusionEffect(Set.of(fc2), Set.of(f));
  }

  private void setupInclusion() {
    // retry on F
    nodeF_retry = nodeA.createChild()
        .withPoint("F", "f1", 1);
    f2 = nodeF_retry.getBehaviour();
    nodeG_F_retry = nodeF.createChild()
        .withPoint("G", "g1");
    g2 = nodeG_F_retry.getBehaviour();
    Behaviour ff1 = new Behaviour(f.uid(), mode1);
    Behaviour ff2 = new Behaviour(f.uid(), mode2);
    store.addUpstreamCause(f2, Set.of(g2));
    store.addInclusionEffect(Set.of(ff1), Set.of(f2));
    store.addInclusionEffect(Set.of(ff2), Set.of(f2));

    // alternative for B, for mode 1
    nodeB_alt = nodeA.createChild()
        .withPoint("B_prime", "b1", 0);
    bprime = nodeB_alt.getBehaviour();
    Behaviour fb = new Behaviour(b.uid(), mode1);
    store.addInclusionEffect(Set.of(fb), Set.of(bprime));

  }

  private int faultyBehaviours(Set<Behaviour> s) {
    return (int) (s.stream().filter(b -> b.isFault()).count());
  }

  @Test
  public void testUpstream() {
    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of());
    assertEquals(7, result.size());
    assertEquals(0, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtA() {
    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(
        new Fault(a.uid(), mode1)));
    assertEquals(1, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtB() {
    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(b.uid(), mode1)));
    assertEquals(7, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtC() {
    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(c.uid(), mode1)));
    assertEquals(7 - 2, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDirectCausalAtF() {
    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(f.uid(), mode1)));
    assertEquals(7 - 1, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testHideB() {
    setupExclusion();

    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(b.uid(), mode1)));
    assertEquals(2, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testHideC() {
    setupExclusion();

    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(c.uid(), mode1)));
    assertEquals(3, result.size());
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamD() {
    setupDownstream();

    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(d.uid(), mode1)));
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamE() {
    setupDownstream();

    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(e.uid(), mode1)));
    assertEquals(2, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamG() {
    setupDownstream();

    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(g.uid(), mode1)));
    assertEquals(1, faultyBehaviours(result));
  }

  @Test
  public void testDownstreamDE() {
    setupDownstream();

    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(d.uid(), mode1), new Fault(e.uid(), mode2)));
    assertEquals(3, faultyBehaviours(result));
  }

  @Test
  public void testHideDDownstream() {
    setupDownstream();
    setupExclusion();

    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(d.uid(), mode1)));
    assertEquals(5, result.size());
    assertEquals(2, faultyBehaviours(result));
  }
}
