/*
 * Copyright (c) Lightstreamer Srl
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package jms_demo_services.stocklist;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simulates a data feed that supplies quote values for all the
 * stocks needed for the Demo.
 */
public class FeedSimulator {

  private static final ScheduledExecutorService dispatcher = Executors.newScheduledThreadPool(2);

  private static final Random random = new Random();

    /**
     * Used to automatically generate the updates for the 30 stocks:
     * mean and standard deviation of the times between consecutive
     * updates for the same stock.
     */
    private static final double[] UPDATE_TIME_MEANS = {30000, 500, 3000, 90000,
                                                     7000, 10000, 3000, 7000,
                                                     7000, 7000, 500, 3000,
                                                     20000, 20000, 20000, 30000,
                                                     500, 3000, 90000, 7000,
                                                     10000, 3000, 7000, 7000,
                                                     7000, 500, 3000, 20000,
                                                     20000, 20000, };

    private static final double[] UPDATE_TIME_STD_DEVS = {6000, 300, 1000, 1000,
                                                         100, 5000, 1000, 3000,
                                                         1000, 6000, 300, 1000,
                                                         1000, 4000, 1000, 6000,
                                                         300, 1000, 1000, 100,
                                                         5000, 1000, 3000, 1000,
                                                         6000, 300, 1000, 1000,
                                                         4000, 1000, };

  /**
   * Used to generate the initial field values for the 30 stocks.
   */
  private static final double[] REF_PRICES = {3.04, 16.09, 7.19, 3.63, 7.61,
                                             2.30, 15.39, 5.31, 4.86, 7.61,
                                             10.41, 3.94, 6.79, 26.87, 2.27,
                                             13.04, 6.09, 17.19, 13.63, 17.61,
                                             11.30, 5.39, 15.31, 14.86, 17.61,
                                             5.41, 13.94, 16.79, 6.87,
                                             11.27, };

  private static final double[] OPEN_PRICES = {3.10, 16.20, 7.25, 3.62, 7.65,
                                              2.30, 15.85, 5.31, 4.97, 7.70,
                                              10.50, 3.95, 6.84, 27.05, 2.29,
                                              13.20, 6.20, 17.25, 13.62,
                                              17.65, 11.30, 5.55, 15.31,
                                              14.97, 17.70, 5.42, 13.95,
                                              16.84, 7.05, 11.29, };

  private static final double[] MIN_PRICES = {3.09, 15.78, 7.15, 3.62, 7.53,
                                             2.28, 15.60, 5.23, 4.89, 7.70,
                                             10.36, 3.90, 6.81, 26.74, 2.29,
                                             13.09, 5.78, 17.15, 13.62, 17.53,
                                             11.28, 5.60, 15.23, 14.89, 17.70,
                                             5.36, 13.90, 16.81, 6.74,
                                             11.29, };

  private static final double[] MAX_PRICES = {3.19, 16.20, 7.26, 3.71, 7.65,
                                             2.30, 15.89, 5.31, 4.97, 7.86,
                                             10.50, 3.95, 6.87, 27.05, 2.31,
                                             13.19, 6.20, 17.26, 13.71, 17.65,
                                             11.30, 5.89, 15.31, 14.97, 17.86,
                                             5.50, 13.95, 16.87, 7.05,
                                             11.31, };

  private static final String[] STOCK_NAMES = {"Anduct", "Ations Europe",
                                              "Bagies Consulting", "BAY Corporation",
                                              "CON Consulting", "Corcor PLC",
                                              "CVS Asia", "Datio PLC",
                                              "Dentems", "ELE Manufacturing",
                                              "Exacktum Systems", "KLA Systems Inc",
                                              "Lted Europe", "Magasconall Capital",
                                              "MED", "Mice Investments",
                                              "Micropline PLC", "Nologicroup Devices",
                                              "Phing Technology", "Pres Partners",
                                              "Quips Devices", "Ress Devices",
                                              "Sacle Research", "Seaging Devices",
                                              "Sems Systems, Inc", "Softwora Consulting",
                                              "Systeria Develop", "Thewlec Asia",
                                              "Virtutis", "Yahl" };

  /**
   * Used to keep the contexts of the 30 stocks.
   */
  private final List<MyProducer> stockGenerators = new ArrayList<MyProducer>();

  /**
   * The internal listener for the update events.
   */
  private final FeedListener listener;

  public FeedSimulator(FeedListener listener) {
    this.listener = listener;
  }

  /**
   * Starts generating update events for the stocks. Simulates attaching and reading from an
   * external broadcast feed.
   */
  public void start() {
    for (int i = 0; i < 30; i++) {
      MyProducer myProducer = new MyProducer("item" + (i + 1), i);
      stockGenerators.add(myProducer);
      long waitTime = myProducer.computeNextWaitTime();
      scheduleGenerator(myProducer, waitTime);
    }
  }

  /**
   * Generates new values and sends a new update event at the time the producer declared to do it.
   */
  private void scheduleGenerator(final MyProducer producer, long waitTimeMillis) {
    dispatcher.schedule(() -> {
      long nextWaitTime = 0;

      synchronized (producer) {
        producer.computeNewValues();

        listener.onFeedUpdate(producer.itemName, producer.getCurrentValues(), false);
        nextWaitTime = producer.computeNextWaitTime();
      }

      scheduleGenerator(producer, nextWaitTime);
    }, waitTimeMillis, TimeUnit.MILLISECONDS);
  }

  /*
   * Manages the current state and generates update events for a single stock.
   */
  private class MyProducer {

    private final String itemName;

    private final String stockName;

    private final int open, ref;

    private final double mean, stddev;

    private int last, min, max, other;

    /**
     * Initializes stock data based on the already prepared values.
     */
    public MyProducer(String itemName, int itemPos) {
      this.itemName = itemName;

      // All prices are converted in integer form to simplify the
      // management; they will be converted back before being sent
      // in the update events
      open = (int) Math.round(OPEN_PRICES[itemPos] * 100);
      ref = (int) Math.round(REF_PRICES[itemPos] * 100);
      min = (int) Math.ceil(MIN_PRICES[itemPos] * 100);
      max = (int) Math.floor(MAX_PRICES[itemPos] * 100);
      stockName = STOCK_NAMES[itemPos];
      last = open;
      mean = UPDATE_TIME_MEANS[itemPos];
      stddev = UPDATE_TIME_STD_DEVS[itemPos];
    }

    /**
     * Decides, for ease of simulation, the time at which the next update for the stock will happen.
     */
    public long computeNextWaitTime() {
      long millis;

      do {
        millis = (long) gaussian(mean, stddev);
      } while (millis <= 0);

      return millis;
    }

    /**
     * Changes the current data for the stock.
     */
    public void computeNewValues() {

      // This stuff is to ensure that new prices follow a random
      // but nondivergent path, centered around the reference price
      double limit = ref / 4.0;
      int jump = ref / 100;
      double relDist = (last - ref) / limit;
      int direction = 1;

      if (relDist < 0) {
        direction = -1;
        relDist = -relDist;
      }

      if (relDist > 1) {
        relDist = 1;
      }

      double weight = (relDist * relDist * relDist);
      double prob = (1 - weight) / 2;
      boolean goFarther = random.nextDouble() < prob;

      if (!goFarther) {
        direction *= -1;
      }

      int difference = uniform(0, jump) * direction;
      int gap = ref / 250;
      int delta;

      if (gap > 0) {
        do {
          delta = uniform(-gap, gap);
        } while (delta == 0);

      } else {
        delta = 1;
      }

      last += difference;
      other = last + delta;

      if (last < min) {
        min = last;
      }

      if (last > max) {
        max = last;
      }
    }

    /**
     * Picks the stock field values and stores them in a <field->value> HashMap. If fullData is
     * false, then only the fields whose value is just changed are considered (though this check is
     * not strict).
     */
    public Map<String, String> getCurrentValues() {
      final HashMap<String, String> event = new HashMap<String, String>();

      String format = "HH:mm:ss";
      SimpleDateFormat formatter = new SimpleDateFormat(format);
      String time = formatter.format(new Date());
      event.put("time", time);
      addDecField("last_price", last, event);

      if (other > last) {
        addDecField("ask", other, event);
        addDecField("bid", last, event);
      } else {
        addDecField("ask", last, event);
        addDecField("bid", other, event);
      }

      int quantity;
      quantity = uniform(1, 200) * 500;
      event.put("bid_quantity", Integer.toString(quantity));
      quantity = uniform(1, 200) * 500;
      event.put("ask_quantity", Integer.toString(quantity));
      double var = (last - ref) / (double) ref * 100;
      addDecField("pct_change", (int) (var * 100), event);
      addDecField("min", min, event);
      addDecField("max", max, event);

      event.put("stock_name", stockName);
      addDecField("ref_price", ref, event);
      addDecField("open_price", open, event);
      event.put("item_status", "active");

      return event;
    }
  }

  private void addDecField(String fld, int val100, HashMap<String, String> target) {
    double val = (((double) val100) / 100);
    String buf = Double.toString(val);
    target.put(fld, buf);
  }

  private double gaussian(double mean, double stddev) {
    double base = random.nextGaussian();
    return base * stddev + mean;
  }

  private int uniform(int min, int max) {
    int base = random.nextInt(max + 1 - min);
    return base + min;
  }
}
