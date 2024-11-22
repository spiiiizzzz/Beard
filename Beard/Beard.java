package connectx.Beard;

import java.util.HashMap;
import java.util.Random;
import connectx.CXPlayer;
import connectx.CXGameState;
import connectx.CXBoard;
import connectx.CXCell;
import connectx.CXCellState;


public class Beard implements CXPlayer{
    private CXGameState myWin, enemyWin;
    private boolean     first, firstMove, stopAB;
	private int         timeLimit;
    private int[]       moveOrder;
    private long        startTurn, currentHash;
    private int         M, N, K;
    private Random      rand;
    private long[][][]  zBoard;
    private HashMap<Long, Integer> trsTable, permTable;

    public Beard(){}

    public void initPlayer(int M, int N, int K, boolean first, int timeout_in_secs){
        this.M = M;
        this.N = N;
        this.K = K;
        this.first = first;
        this.firstMove = true;
        this.trsTable = new HashMap<Long, Integer>();
        this.permTable = new HashMap<Long, Integer>();
        this.rand = new Random(System.currentTimeMillis());
        evalMoveOrder();
        initZobristBoard();
        this.myWin    = first ? CXGameState.WINP1 : CXGameState.WINP2;
        this.enemyWin = first ? CXGameState.WINP2 : CXGameState.WINP1;
        this.timeLimit = timeout_in_secs;
    }

    public int selectColumn(CXBoard B) {
        startTurn = System.currentTimeMillis();
        stopAB = false;

        if (firstMove){ //play the center move
            firstMove = false;
            return N/2;
        }

        int finalChoice = IterativeDeepening(B);

        if (finalChoice == -1) finalChoice = panicChoice(B);

        return finalChoice;
    }

    // start calculating moves and choose the best one
    //  O(\sqrt(b^d)) see below
    private int IterativeDeepening(CXBoard B){
        int best, intermediateChoice = -1, newBest, finalChoice = -1, depth = 0;
        currentHash = computeHash(B);

        while (!timeout()){
            best = Integer.MIN_VALUE;

            // check if we have already iterated through the whole tree
            // and therefore additional iterations are useless
            // (it usually happens in the final phases of the game)
            if (stopAB) break;
            stopAB = true;

            // clear the table
            trsTable.clear();

            try {
                for (int i : moveOrder){
                    if (!B.fullColumn(i)){
                        B.markColumn(i);
                        if (B.gameState() == myWin) return i;
                        newBest = AlphaBeta(B, false, Integer.MIN_VALUE, Integer.MAX_VALUE, depth);
                        if (newBest > best) {
                            best = newBest;
                            intermediateChoice = i;
                        }
                        B.unmarkColumn();
                        if (newBest == Integer.MAX_VALUE) break;
                    }
                }
            } catch (Exception e){    // Time's up
                break;
            }

            depth++;
            finalChoice = intermediateChoice;
        }
        
        return finalChoice;
    }

    // alphabeta implementation
    //  O(b^d) where b = branching factor, d = depth of search
    //  on avarage we have O(\sqrt(b^d))
    private int AlphaBeta(CXBoard B, boolean max, int alpha, int beta, int depth) throws Exception {
        // stop iterating if time is running out
        if (timeout()) throw new Exception("TIMEOUT");
        
        // compute the current zobrist hash for this board
        CXCell C = B.getLastMove();
        long lastElementHash = zBoard[C.i][C.j][C.state == CXCellState.P1 ? 0 : 1];
        currentHash ^= lastElementHash;

        // check if the current board has already been evaluated
        // return the previus eval if it has
        if (permTable.containsKey(currentHash)) {
            long tmp = currentHash;
            currentHash ^= lastElementHash;
            return permTable.get(tmp);
        }
        if (trsTable.containsKey(currentHash)) {
            long tmp = currentHash;
            currentHash ^= lastElementHash;
            return trsTable.get(tmp);
        }

        int eval;

        if (depth == 0 || (B.gameState() != CXGameState.OPEN)){
            // check if we have visited the entire game tree
            if (depth == 0 && B.gameState() == CXGameState.OPEN) stopAB = false; 
            eval = evaluate(B);
        } else if (max) {
            eval = Integer.MIN_VALUE;
            for (int i : moveOrder){
                if (!B.fullColumn(i) && !nextMoveEnd(B, i, false)){
                    B.markColumn(i);
                    eval = Math.max(eval, AlphaBeta(B, !max, alpha, beta, depth-1));
                    alpha = Math.max(eval, alpha);
                    B.unmarkColumn();
                }
                if (beta <= alpha) break;
            }
        } else {
            eval = Integer.MAX_VALUE;
            for (int i : moveOrder){
                if (!B.fullColumn(i) && !nextMoveEnd(B, i, true)){
                    B.markColumn(i);
                    eval = Math.min(eval, AlphaBeta(B, !max, alpha, beta, depth-1));
                    beta = Math.min(eval, beta);
                    B.unmarkColumn();
                }
                if (beta <= alpha) break;
            }
        }

        // if the position leads to a win or a loss, put it in the permanent table
        if (eval == Integer.MAX_VALUE || eval == Integer.MIN_VALUE) permTable.put(currentHash, eval);
        else trsTable.put(currentHash, eval);
        currentHash ^= lastElementHash;
        return eval;
    }

    // evaluate board score
    //  Theta((M)(N-K)(K)) worst case scenario
    private int evaluate(CXBoard B){
        if (B.gameState() == CXGameState.OPEN) return heuristic(B); // O(MNK-MK^2-NK^2+K^3)
        if (B.gameState() == CXGameState.DRAW) return 0;            // O(1)
        if (B.gameState() == myWin) return Integer.MAX_VALUE;       // O(1)
        else return Integer.MIN_VALUE;                              // O(1)
    }

    // heuristic function to evaluate the board
    //  Theta((M)(N-K)(K))
    private int heuristic(CXBoard B){
        CXCellState[][] board = B.getBoard();
        int eval = 0;

        /*
           check columns 
           for each column, check if the control of that column is yours or
           your opponent's. Add a score relative to the importance of that column
           where the central column is the most important and the sides are the least important
         */
        //  Theta(MN)
        for (int i = 0; i < N; i++){
            eval += checkSingleColumn(board, i);
        }

        
        /* 
           check rows
           for each row allignment, check if there is enough space to make a win
           if there is: add a score relative to the number of tokens alligned
         */
        //  Theta((M)(N-K)(K))
        for (int i = 0; i < M; i++){
            for (int j = 0; j < N-K; j++){
                eval += checkSingleRow(board, i, j);
            }
        }

        // check diagonals
        // same thing as above but for diagonal allignments
        //  Theta((M-K)(N-K)(K))
        for (int i = 0; i < M-K; i++){
            for (int j = 0; j < N-K; j++){
                eval += checkSingleDiagonal(board, i, j, i+K, j+K);
            }
        }
        for (int i = 0; i < M-K; i++){
            for (int j = N-1; j > K-1; j--){
                eval += checkSingleAntiDiagonal(board, i, j, i+K, j-K);
            }
        }

        return eval;
    }

    //  Theta(N)
    private int checkSingleColumn(CXCellState[][] board, int n){
        int P1Count = 0, P2Count = 0;
        for (int j = 0; j < M; j++){
            switch (board[j][moveOrder[n]]){
                case P1:
                    P1Count++;
                    break;
                case P2:
                    P2Count++;
                    break;
                case FREE:
                    break;
            }
            // there is no need to keep counting if every other cell is Free
            if (board[j][moveOrder[n]] == CXCellState.FREE) break;
        }
        return P1Count*(N-n) - P2Count*(N-n);
    }

    //  Theta((N-K)K)
    private int checkSingleRow(CXCellState[][] board, int m, int n){
        int P1Count = 0, P2Count = 0, eval = 0;
        for (int j = 0; j < K; j++){
            switch (board[m][n+j]){
                case P1:
                    P1Count++;
                    break;
                case P2:
                    P2Count++;
                    break;
                case FREE:
                    break;
            }
        }
        if (P1Count == 0 && P2Count == 0) {
            return 0;
        } else if (P1Count == 0){
            return first ? -(10 * P2Count) : (10 * P2Count);
        } else if (P2Count == 0){
            return first ? (10 * P1Count) : -(10 * P1Count);
        }
        return 0;
    }

    //  Theta(K)
    private int checkSingleDiagonal(CXCellState[][] board, int start1, int start2, int end1, int end2){
        int P1Count = 0, P2Count = 0;
        for (int i = start1, j = start2; i < end1 && j < end2;){
            switch (board[i][j]){
                case P1:
                    P1Count++;
                    break;
                case P2:
                    P2Count++;
                    break;
                case FREE:
                    break;
            }
            i++;j++;
        }
        if (P1Count == 0 && P2Count == 0){
            return 0;
        } else if (P1Count == 0){
            return first ? -(10 * P2Count) : (10 * P2Count);
        } else if (P2Count == 0){
            return first ? (10 * P1Count) : -(10 * P1Count);
        }
        return 0;
    }

    //  Theta(K)
    private int checkSingleAntiDiagonal(CXCellState[][] board, int start1, int start2, int end1, int end2){
        int P1Count = 0, P2Count = 0;
        for (int i = start1, j = start2; i < end1 && j > end2;){
            switch (board[i][j]){
                case P1:
                    P1Count++;
                    break;
                case P2:
                    P2Count++;
                    break;
                case FREE:
                    break;
            }
            i++;j--;
        }
        if (P1Count == 0 && P2Count == 0){
            return 0;
        } else if (P1Count == 0){
            return first ? -(10 * P2Count) : (10 * P2Count);
        } else if (P2Count == 0){
            return first ? (10 * P1Count) : -(10 * P1Count);
        }
        return 0;
    }

    // evaluate the order with which we should evaluate the different moves
    // starting from the center going to the sides
    //  Theta(N) (doesn't matter since it only gets executed once when the player is initialized)
    private void evalMoveOrder(){
        moveOrder = new int[N];
        if (N % 2 == 0){
            for (int i = 0, j = 0; i < N; i++){
                moveOrder[i] = N / 2 + j;
                i++; j++;
                moveOrder[i] = N / 2 - j;
            }
        } else {
            moveOrder[0] = N / 2;
            for (int i = 1, j = 1; i < N; i++){
                moveOrder[i] = N / 2 + j;
                i++;
                moveOrder[i] = N / 2 - j;
                j++;
            }

        }
    }

    // initialize the Zobrist board
    //  Theta(MN) (it only gets executed once when the player is initialized)
    private void initZobristBoard(){
        zBoard = new long[M][N][2];
        for (int i = 0; i < M; i++){                    // for each row of the board
            for (int j = 0; j < N; j++){                // for each column of the board
                zBoard[i][j][0] = rand.nextLong();      // for each player
                zBoard[i][j][1] = rand.nextLong();      // set a random hash
            }
        }
    }

    // compute Zobrist hash for the board
    //  Theta(MN) (it only gets executed once at the start of the turn)
    private Long computeHash(CXBoard B){
        CXCell[] cells = B.getMarkedCells();
        Long hash = 0L; // starting value
        for (CXCell C : cells){
            hash ^= zBoard[C.i][C.j][C.state == CXCellState.P1 ? 0 : 1];
        }
        return hash;
    }

    // check if the column n leads to an instant win or loss
    //  Theta(1) worst case scenario, if the move does not lead to a win or a loss
    private boolean nextMoveEnd(CXBoard B, int n, boolean win){
        B.markColumn(n);
        if (B.gameState() != CXGameState.OPEN || B.fullColumn(n)) {
            B.unmarkColumn();
            return false;
        }
        if (B.markColumn(n) == (win ? myWin : enemyWin)){
            B.unmarkColumn();
            B.unmarkColumn();
            return true;
        }
        B.unmarkColumn();
        B.unmarkColumn();
        return false;
    }

    // used only in debug
    private void printBoard(CXBoard B, int eval){
        System.out.println("-------------");
        if (B == null) System.out.println("null");
        else {
            System.out.println("eval: " + eval);
            for (int i = 0; i < B.M; i++){
                for (int j = 0; j < B.N; j++){
                    switch (B.cellState(i, j)){
                        case FREE:
                            System.out.print("-");
                            break;
                        case P1:
                            System.out.print("O");
                            break;
                        case P2:
                            System.out.print("X");
                            break;
                    }
                }
                System.out.print("\n");
            }
        }
    }

    // check if there is enough time left, can be tweaked to taste
    //  Theta(1)
    private boolean timeout(){
        return (System.currentTimeMillis() - startTurn) / 1000.0 >= timeLimit * (93.0 / 100.0);
    }

    // make a choice based purely on the heuristic function
    // it should be used only when the main algorithm cannot find a playable move
    private int panicChoice(CXBoard B){
        int newBest = Integer.MIN_VALUE, best = Integer.MIN_VALUE, finalChoice = -1;
        for (int i : moveOrder){
            if (!B.fullColumn(i) && !nextMoveEnd(B, i, false)){
                B.markColumn(i);
                if (newBest > best){
                    best = newBest;
                    finalChoice = i;
                }
                B.unmarkColumn();
            }
        }

        // the algorithm should never be able to reach this piece of code
        // but it is still here, just in case
        if (finalChoice == -1) {
            Integer[] aCols = B.getAvailableColumns();
            return aCols[rand.nextInt(0, aCols.length-1)];
        } 

        return finalChoice;
    }

    public String playerName(){
        return "Beard";
    }
}
