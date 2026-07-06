package nl.stokpop.scramjet.controller;

import io.swagger.v3.oas.annotations.Operation;
import nl.stokpop.scramjet.ScramjetProperties;
import nl.stokpop.scramjet.domain.BurnerMessage;
import nl.stokpop.scramjet.matrix.InvalidMatrixException;
import nl.stokpop.scramjet.matrix.MatrixCalculator;
import nl.stokpop.scramjet.matrix.MatrixEqualResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CpuBurner {

    private static final Logger log = LoggerFactory.getLogger(CpuBurner.class);

    private final ScramjetProperties props;

    public CpuBurner(final ScramjetProperties props) {
        this.props = props;
    }

    /**
     * Spend some time on CPU doing some magik matrix calculations.
     */
    @Operation(summary = "Spend some time on CPU doing some magik matrix calculations.")
    @GetMapping("/cpu/magic-identity-check")
    public BurnerMessage magicIdentityCheck(
            @RequestParam(value = "matrixSize", defaultValue = "10") int matrixSize) throws InvalidMatrixException {

        long startTime = System.currentTimeMillis();

        log.info("Calculate magik matrix identity for matrix size [{}].", matrixSize);

        long[][] simpleMagicSquare = MatrixCalculator.simpleMagicSquare(matrixSize);
        long[][] identitySquare = MatrixCalculator.identitySquare(matrixSize);

        long[][] multiplyMatrix = MatrixCalculator.multiply(simpleMagicSquare, identitySquare);

        MatrixEqualResult matrixEqualResult = MatrixCalculator.areEqual(simpleMagicSquare, multiplyMatrix);

        String message = "A simple magic square multiplied by an identity square of size [%d] [%s] to the magic square."
                .formatted(matrixSize, matrixEqualResult.areEqual() ? "is equal" : "is not equal");

        long durationMillis = System.currentTimeMillis() - startTime;
        return new BurnerMessage(message, props.name(), durationMillis);
    }
}
