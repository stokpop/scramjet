package nl.stokpop.scramjet.matrix;

public final class MatrixCalculator {

    public static final boolean featureToggleIdentityMatrix =
            "true".equalsIgnoreCase(System.getenv().getOrDefault("featureToggleIdentityMatrix", "false"));

    private MatrixCalculator() {
    }

    /**
     * To multiply an m×n matrix by an n×p matrix, the ns must be the same,
     * and the result is an m×p matrix.
     */
    public static long[][] multiply(long[][] matrixA, long[][] matrixB) throws InvalidMatrixException {
        final int matrixAm = matrixA.length;
        final int matrixBn = matrixB.length;

        if (matrixAm == 0) { throw new InvalidMatrixException("MatrixA should have at least 1 row"); }
        if (matrixBn == 0) { throw new InvalidMatrixException("MatrixB should have at least 1 row"); }

        final int matrixAn = matrixA[0].length;
        final int matrixBp = matrixB[0].length;

        if (matrixAn == 0) { throw new InvalidMatrixException("MatrixA should have at least 1 column"); }
        if (matrixBp == 0) { throw new InvalidMatrixException("MatrixB should have at least 1 column"); }

        if (matrixAn != matrixBn) {
            throw new InvalidMatrixException(
                    "MatrixA has %d columns, MatrixB has %d rows, which should be equal.".formatted(matrixAn, matrixBn));
        }

        if (featureToggleIdentityMatrix) {
            if (isIdentitySquare(matrixA)) {
                return matrixB;
            }
            if (isIdentitySquare(matrixB)) {
                return matrixA;
            }
        }

        final long[][] matrixC = new long[matrixAm][matrixBp];

        for (int m = 0; m < matrixAm; m++) {
            for (int p = 0; p < matrixBp; p++) {
                long sum = 0;
                for (int n = 0; n < matrixAn; n++) {
                    sum = sum + (matrixA[m][n] * matrixB[n][p]);
                }
                matrixC[m][p] = sum;
            }
        }
        return matrixC;
    }

    private static boolean isIdentitySquare(long[][] matrix) throws InvalidMatrixException {
        return areEqual(identitySquare(matrix.length), matrix).areEqual();
    }

    public static MatrixEqualResult areEqual(final long[][] matrixA, final long[][] matrixB) throws InvalidMatrixException {
        final int matrixAx = matrixA.length;
        final int matrixBx = matrixB.length;

        if (matrixAx == 0) { throw new InvalidMatrixException("MatrixA should have at least 1 row"); }
        if (matrixBx == 0) { throw new InvalidMatrixException("MatrixB should have at least 1 row"); }

        final int matrixAy = matrixA[0].length;
        final int matrixBy = matrixB[0].length;

        if (matrixAy == 0) { throw new InvalidMatrixException("MatrixA should have at least 1 column"); }
        if (matrixBy == 0) { throw new InvalidMatrixException("MatrixB should have at least 1 column"); }

        if (matrixAx != matrixBx) {
            return new MatrixEqualResult(false,
                    "MatrixA has %d rows, MatrixB has %d rows, which should be equal.".formatted(matrixAx, matrixBx));
        }

        if (matrixAy != matrixBy) {
            return new MatrixEqualResult(false,
                    "MatrixA has %d columns, MatrixB has %d columns, which should be equal.".formatted(matrixAy, matrixBy));
        }

        for (int x = 0; x < matrixAx; x++) {
            for (int y = 0; y < matrixAy; y++) {
                if (matrixA[x][y] != matrixB[x][y]) {
                    return new MatrixEqualResult(false,
                            "MatrixA has value %d in [%d][%d], MatrixB value %d, which should be equal."
                                    .formatted(matrixA[x][y], x, y, matrixB[x][y]));
                }
            }
        }
        return new MatrixEqualResult(true, "Matrices are equal.");
    }

    /**
     * Create a simple magic square matrix, where each row and each column contains each number just once.
     */
    public static long[][] simpleMagicSquare(int size) {
        long[][] square = new long[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                square[i][j] = ((j + i) % size) + 1;
            }
        }
        return square;
    }

    public static long[][] identitySquare(int size) {
        long[][] square = new long[size][size];
        for (int i = 0; i < size; i++) {
            square[i][i] = 1;
        }
        return square;
    }
}
