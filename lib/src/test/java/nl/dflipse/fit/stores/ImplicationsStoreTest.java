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
  EventBuilder nodeB;
  EventBuilder nodeC;
  EventBuilder nodeD;
  EventBuilder nodeE;
  EventBuilder nodeF;
  EventBuilder nodeG;

  Behaviour a;
  Behaviour b;
  Behaviour c;
  Behaviour d;
  Behaviour e;
  Behaviour f;
  Behaviour g;

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

  private void setUpHb() {
    // B before c and e
    Behaviour fb1 = new Behaviour(b.uid(), mode1);
    Behaviour fb2 = new Behaviour(b.uid(), mode2);
    store.addSubstitution(Set.of(fb1, c, e), Set.of(fb1));
    store.addSubstitution(Set.of(fb2, c, e), Set.of(fb2));

    // C before e
    Behaviour fc1 = new Behaviour(c.uid(), mode1);
    Behaviour fc2 = new Behaviour(c.uid(), mode2);
    store.addSubstitution(Set.of(b, fc1, e), Set.of(b, fb2));
    store.addSubstitution(Set.of(b, fc2, e), Set.of(b, fb2));
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
  public void testHide() {
    setUpHb();

    Set<Behaviour> result = store.getBehaviours(a.uid(), Set.of(new Fault(b.uid(), mode1)));
    assertEquals(2, result.size());
    assertEquals(1, faultyBehaviours(result));
  }
}
