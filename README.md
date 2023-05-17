# CQMinimizer and Minibase

This project contains two main components which provide unique capabilities that are essential for executing and analysing conjunctive queries.

NB - The code has already been compiled.

## Task 1 : CQMinimizer
The objective of this task is to identify the most streamlined, minimised equivalent version of a given input query over a database, which is a crucial and valuable process for improving query performance.

CQMinimization finds its inspiration from the famous N-Queens problem, which is the backtracking problem that bears the strongest similarity to it. 
In the N-Queens problem, the goal is to place N chess queens on an NxN chessboard so that no two queens attack each other.
This problem is similar to the CQMinimization problem because both of these involve searching for a valid configuration while considering a set of constraints. 
In the N-Queens problem, the constraints are that no two queens can be placed on the same row, column, or diagonal. 
Similarly, in the CQMinimization problem, the constraints are that the homomorphism conditions must be satisfied between the source and the target queries by traversing down a path in the tree to identify a set of feasible mappings. 
Both problems use backtracking to find all possible solutions by exploring the search space exhaustively and backtrack when a solution violates the constraints.

The implementation uses the above logic and adheres to the lecture notes presented during Weeks 2 and 3 of the course. Further details can be found in the code comments of the `CQMinimizer.java` class.

To run the code, navigate to `CQMinimizer.java` located in `src/main/java/ed/inf/adbs/minibase`, and execute it in the terminal using the command below.
It assumes that the output directory already exists.

    java -cp target/minibase-1.0.0-jar-with-dependencies.jar \ 
    ed.inf.adbs.minibase.CQMinimizer \
    data/minimization/input/<query_input_file_name>.txt \
    data/minimization/output/<query_output_file_name>.txt

## Task 2 : Minibase
The objective of this task is to translate conjunctive queries into relational algebra query plans and evaluate them over a mock database.

Minibase currently supports the following relational operators 
- ScanOperator 
- SelectOperator 
- ProjectOperator 
- JoinOperator 
- SumOperator

Each of the above operators extend the `Operator` class

The implementation follows the `Iterator` model which was covered in Week 7 of the course.

The code comments for `filterComparisonAtoms` in `src/main/java/ed/inf/adbs/minibase/evaluation/QueryPlanner.java` and 
`hasNoConflictingSubstitutions` in `src/main/java/ed/inf/adbs/minibase/base/operators/JoinOperator.java` describe the logic for extracting join conditions.

To run the code, navigate to `Minibase.java` located in `src/main/java/ed/inf/adbs/minibase`, and execute it in the terminal using the command below.
It assumes that the output directory already exists.

    java -cp target/minibase-1.0.0-jar-with-dependencies.jar \
    ed.inf.adbs.minibase.Minibase \
    data/evaluation/<database_directory> \
    data/evaluation/input/<query_input_file_name>.txt \
    data/evaluation/output/<query_output_file_name>.csv

## Task 3 : Query Optimisation

The following heuristics are applied to the current implementation in order to reduce execution costs, resulting in a cheaper plan to execute.

1. Selection Pushdown
   1. We know that selection is essentially free while joins are expensive.
   2. We apply selections as soon as we have the relevant columns in the comparison atoms.
   3. Filtering as early as possible reduces the number of tuples that need to be returned to any higher level joins.
   4. This essentially indicates that the children of joins are either selects or scans, and this distinction has important implications for performance.
2. Avoiding Cartesian Products
   1. We filter the comparison atoms to identify relational atoms matching join predicates and iteratively perform theta-joins in our left-deep join tree to avoid computing cartesian products between relational atoms.
3. We also perform equi-joins by identifying conflicting substitutions for the same variable across relational atoms, which eliminates the need to frame these checks as separate selection predicates.
4. Project and Group-by Sum Aggregation
   1. The root of the tree is either a project or a group-by aggregate depending on what appears in the query head.
   2. The project works consistently in cases where all or only some of the variables from the relational atoms are returned.
   3. The group-by sum operator relies on project to retrieve values from its child tuples for the product terms.