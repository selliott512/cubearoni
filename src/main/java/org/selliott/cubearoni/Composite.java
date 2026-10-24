package org.selliott.cubearoni;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A composite solver that is made up of multiple sub-solvers. This class mostly
 * just initializes the sub-solvers and then delegates to them.
 */
public abstract class Composite extends Solver {
  protected abstract List<String> getSubSolverNames();

  protected Map<String, Solver> subSolvers = new HashMap<>();

  // Convenience fields for every possible sub-solver.
  protected Cross cross;
  protected F2L f2l;
  protected LBSlotSafe lbSlotSafe;
  protected LBSlotUnsafe lbSlotUnsafe;
  protected OneSide oneSide;

  protected Composite()
  {
    this(null);
  }

  protected Composite(final String name)
  {
    super(name);
    // For composite do it later due to a tricky initialization order issue.
    setDefaultDBDirAndURL();

    subSolvers.clear();
    for (final String subSolverName : getSubSolverNames()) {
      try {
        final Solver solver = Solver.getSolver(subSolverName);
        solver.setDefaultDBDirAndURL();
        subSolvers.put(subSolverName, solver);
      } catch (final CubearoniException e) {
        // Since an exception seems unlikely and since it would be nice to not
        // have to declare this method as throwing an exception, wrap it in a
        // RuntimeException.
        throw new RuntimeException(e);
      }
    }

    // It's easier to just assign nulls than figure out which ones dynamically.
    cross = (Cross) subSolvers.get(Cross.NAME);
    f2l = (F2L) subSolvers.get(F2L.NAME);
    lbSlotSafe = (LBSlotSafe) subSolvers.get(LBSlotSafe.NAME);
    lbSlotUnsafe = (LBSlotUnsafe) subSolvers.get(LBSlotUnsafe.NAME);
    oneSide = (OneSide) subSolvers.get(OneSide.NAME);
  }

  // Delegation methods for sub-solvers.

  @Override
  public void loadDB() throws CubearoniException {
    // Solver.loadDB() is or binary databases whereas a composite solver will
    // most likely have a text file, if anything. So we don't call super.loadDB().
    for (final Solver subSolver : subSolvers.values()) {
      subSolver.loadDB();
    }
  }

  @Override
  public void setVerbose(final boolean verbose) {
    super.setVerbose(verbose);
    for (final Solver subSolver : subSolvers.values()) {
      subSolver.setVerbose(verbose);
    }
  }

  @Override
  public void setLeftHanded(final boolean leftHanded) {
    super.setLeftHanded(leftHanded);
    for (final Solver subSolver : subSolvers.values()) {
      subSolver.setLeftHanded(leftHanded);
    }
  }

  @Override
  public void setDBDir(final String dbDir) {
    super.setDBDir(dbDir);
    for (final Solver subSolver : subSolvers.values()) {
      subSolver.setDBDir(dbDir);
    }
  }

  @Override
  public void setDBURL(final URL dbURLDir) throws CubearoniException {
    super.setDBURL(dbURLDir);
    for (final Solver subSolver : subSolvers.values()) {
      subSolver.setDBURL(dbURLDir);
    }
  }

  @Override
  public void setOptions(final Map<String, String> options) {
    super.setOptions(options);
    for (final Solver subSolver : subSolvers.values()) {
      subSolver.setOptions(options);
    }
  }

  @Override
  public void generateDB() throws CubearoniException {
    throw new CubearoniException("Solver " + getName() + " does not generate a "
            + "database.");
  }

  @Override
  public void saveDB() throws CubearoniException {
    throw new CubearoniException("Solver " + getName() + " does not save a database.");
  }

  @Override
  public void test() throws CubearoniException {
    throw new CubearoniException("Solver " + getName() + " has no database to test.");
  }

  @Override
  public int getIndex(final Cube cube) throws CubearoniException {
    throw new CubearoniException("Not supported");
  }

  @Override
  public boolean isComposite() {
    return true;
  }
  @Override
  public int getIndexArrayLength() {
    throw new RuntimeException("Not supported");
  }

  @Override
  public void setIndex(final Cube cube, final int num) {
    throw new RuntimeException("Not supported");
  }
}
