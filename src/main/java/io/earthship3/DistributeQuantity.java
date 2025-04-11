package io.earthship3;

import java.util.List;
import java.util.Scanner;
import java.util.stream.IntStream;

public class DistributeQuantity {

  public static QuantityDistribution distributeAllowLeftover(int quantity, int bucketLimit, int availableBuckets) {
    return distributeQuantity(quantity, bucketLimit, availableBuckets);
  }

  public static QuantityDistribution distributeWithoutLeftover(int quantity, int bucketLimit, int availableBuckets) {
    return quantity > bucketLimit * availableBuckets
        ? distributeQuantity(quantity, availableBuckets)
        : distributeQuantity(quantity, bucketLimit, availableBuckets);
  }

  /**
   * Distributes quantity into buckets according to the following rules:
   * <ol>
   * <li>Use the least number of buckets possible</li>
   * <li>When Q > L * B, fill all buckets to limit L, and return remaining quantity Q1 = Q - L * B</li>
   * <li>When Q <= L * B, distribute quantity as evenly as possible across buckets using integer math</li>
   * </ol>
   *
   * @param quantity         The quantity of quantity to distribute
   * @param bucketLimit      The maximum capacity of each bucket
   * @param availableBuckets The total number of buckets available
   * @return A DistributionResult containing the quantity distribution and any remaining quantity
   */
  private static QuantityDistribution distributeQuantity(int quantity, int bucketLimit, int availableBuckets) {
    // Validate inputs
    if (quantity < 0 || bucketLimit <= 0 || availableBuckets <= 0) {
      throw new IllegalArgumentException("Invalid input parameters");
    }

    // If no quantity, return empty array with no remaining quantity
    if (quantity == 0) {
      return new QuantityDistribution(List.of(), 0);
    }

    // Calculate how many buckets we need
    int bucketsNeeded = Math.min((int) Math.ceil((double) quantity / bucketLimit), availableBuckets);

    // If required buckets exceed available, fill all buckets to limit
    if (quantity > bucketLimit * availableBuckets) {
      var bucketLevels = IntStream.range(0, availableBuckets)
          .map(i -> bucketLimit)
          .boxed()
          .toList();
      return new QuantityDistribution(bucketLevels, quantity - (bucketLimit * availableBuckets));
    }

    // Base amount each bucket gets
    var baseAmount = quantity / bucketsNeeded;

    // Remainder to distribute
    var remainder = quantity % bucketsNeeded;

    // Fill buckets
    var bucketLevels = IntStream.range(0, bucketsNeeded)
        .map(i -> baseAmount + (i < remainder ? 1 : 0))
        .boxed()
        .toList();
    return new QuantityDistribution(bucketLevels, 0);
  }

  /**
   * Distributes quantity into buckets with no bucket quantity limit
   *
   * @param quantity         The quantity of quantity to distribute
   * @param availableBuckets The total number of buckets available
   * @return A DistributionResult containing the quantity distribution and any remaining quantity
   */
  private static QuantityDistribution distributeQuantity(int quantity, int availableBuckets) {
    if (quantity == 0) {
      return new QuantityDistribution(List.of(), 0);
    }

    var bucketsNeeded = Math.min(quantity, availableBuckets);
    var baseAmount = quantity / bucketsNeeded;
    var remainder = quantity % bucketsNeeded;
    var bucketLevels = IntStream.range(0, bucketsNeeded)
        .map(i -> baseAmount + (i < remainder ? 1 : 0))
        .boxed()
        .toList();
    return new QuantityDistribution(bucketLevels, 0);
  }

  public record QuantityDistribution(List<Integer> bucketLevels, int leftoverQuantity) {}

  private static final Scanner scanner = new Scanner(System.in);

  public static void main(String[] args) {
    System.out.println("Distribution Quantity REPL");
    System.out.println("Enter 'exit' at any prompt to quit");

    while (true) {
      try {
        // Read
        System.out.print("Enter quantity (Q): ");
        String qInput = scanner.nextLine();
        if (qInput.equalsIgnoreCase("exit"))
          break;
        var quantity = Integer.parseInt(qInput);

        System.out.print("Enter bucket limit (L): ");
        String lInput = scanner.nextLine();
        if (lInput.equalsIgnoreCase("exit"))
          break;
        var bucketLimit = Integer.parseInt(lInput);

        System.out.print("Enter number of available buckets (B): ");
        String bInput = scanner.nextLine();
        if (bInput.equalsIgnoreCase("exit"))
          break;
        var availableBuckets = Integer.parseInt(bInput);

        // Eval
        var result = (bucketLimit == 0)
            ? DistributeQuantity.distributeQuantity(quantity, availableBuckets)
            : DistributeQuantity.distributeQuantity(quantity, bucketLimit, availableBuckets);

        // Print
        System.out.println("\nResult: " + result.toString());
        System.out.println("Bucket Levels: " + result.bucketLevels());
        System.out.println("Leftover Water: " + result.leftoverQuantity());
        var resultQuantity = result.bucketLevels().stream().mapToInt(Integer::intValue).sum()
            + result.leftoverQuantity();
        System.out.println("Result Quantity: " + resultQuantity);
        System.out.println();
      } catch (NumberFormatException e) {
        System.out.println("Error: Please enter valid integers\n");
      } catch (IllegalArgumentException e) {
        System.out.println("Error: " + e.getMessage() + "\n");
      } catch (Exception e) {
        System.out.println("Unexpected error: " + e.getMessage() + "\n");
      }
    }

    System.out.println("Exiting REPL. Goodbye!");
    scanner.close();
  }
}