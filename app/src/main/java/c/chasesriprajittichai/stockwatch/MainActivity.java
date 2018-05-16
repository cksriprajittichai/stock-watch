package c.chasesriprajittichai.stockwatch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import static org.apache.commons.lang3.StringUtils.substringBetween;


public class MainActivity extends AppCompatActivity implements TaskFinishedListener {


    private class DownloadHalfStockTask extends AsyncTask<String, Integer, ArrayList<HalfStock>> {

        private Context context;
        private TaskFinishedListener taskFinishedListener;

        private DownloadHalfStockTask(Context context, TaskFinishedListener taskFinishedListener) {
            this.context = context;
            this.taskFinishedListener = taskFinishedListener;
        }

        /**
         * Takes tickers as parameters, updates stockList to contain the stocks with tickers.
         *
         * @param tickers Tickers of the stocks to put into stockList.
         * @return stockList, updated with stocks with designated tickers.
         */
        @Override
        protected ArrayList<HalfStock> doInBackground(String... tickers) {
            for (String ticker : tickers) {
                halfStocks.add(new HalfStock(context, ticker));
            }

            return halfStocks;
        }


        protected void onPostExecute(ArrayList<HalfStock> stockList) {
            recyclerView = findViewById(R.id.recycler_view);

            if (stockList.size() == 1) { // Only create RecyclerViewStockAdapter once
                recyclerView.setAdapter(new RecyclerViewStockAdapter(stockList, halfStock -> {
                    Intent intent = new Intent(context, FullStockActivity.class);
                    intent.putExtra("Ticker", halfStock.getTicker());
                    startActivity(intent);
                }));
            } else {
                recyclerView.getAdapter().notifyItemInserted(stockList.size() - 1);
            }
            recyclerView.setLayoutManager(new LinearLayoutManager(context));
            recyclerView.addItemDecoration(new RecyclerViewDivider(context));

            taskFinishedListener.onTaskFinished(stockList.size());
        }
    }


    private RecyclerView recyclerView;
    //    private String[] tickers = {"BAC", "DIS", "BA", "FB", "GE", "GOOGL", "GM", "GS", "HD",
//            "IBM", "JPM", "JNJ", "CSCO", "CTXS", "ADBE", "AXP", "ANTM", "MSFT", "MRK", "CI", "AAPL", "INTC", "FSLR", "CAT", "RTN", "DKS",
//            "AAL", "DWDP", "DAL", "CVX", "DRYS", "AMD", "AMZN", "NVDA", "T", "TRV", "UTX", "BRK-A", "BRK-B"};
    private String[] tickers = {"RTN", "BA", "FB", "GE", "GOOGL", "GM", "GS", "HD",
            "IBM", "JPM", "JNJ", "BAC", "MSFT", "MRK", "AAPL"};
    private ArrayList<HalfStock> halfStocks = new ArrayList<>();
    private DownloadHalfStockTask asyncTask;

    private SharedPreferences preferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Stock Watch");
    }


    @Override
    protected void onResume() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        HashSet<String> tickerSet = new HashSet<>();
        tickerSet = new HashSet<>(preferences.getStringSet("Tickers", tickerSet));

        Iterator<String> tickerSetIt = tickerSet.iterator();
        while (tickerSetIt.hasNext()) {
            String tempTicker = tickerSetIt.next();
            /**
             * DO SOMETHING WITH TEMPTICKER
             */
        }

        if (halfStocks.isEmpty() && tickers.length > 0) { // Only check when needed, and bound check
            asyncTask = new DownloadHalfStockTask(this, this);
            asyncTask.execute(tickers[0]); // Observer pattern will call tasks on the remaining tickers
        }

        super.onResume();
    }


    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putStringSet("Tickers", new HashSet<>(getTickersInHalfStocks()));
        editor.apply();

        super.onPause();
    }


    @Override
    public void onTaskFinished(int stockListSize) {
        if (stockListSize < tickers.length) { // If there are still more tasks to run (more items to add to recycler view)
            asyncTask = new DownloadHalfStockTask(this, this);
            asyncTask.execute(tickers[stockListSize]);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_activity_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sortAlphabeticallyMenuItem:
                /* Sort halfStocks */
                halfStocks.sort((HalfStock a, HalfStock b) -> a.getTicker().compareTo(b.getTicker()));
                recyclerView.getAdapter().notifyItemRangeChanged(0, recyclerView.getAdapter().getItemCount() - 1);
                return true;

            case R.id.sortByPriceMenuItem:
                halfStocks.sort((HalfStock a, HalfStock b) -> {
                    String aPriceStr = a.getStockStat("Price").getValue();
                    String bPriceStr = b.getStockStat("Price").getValue();

                    // Remove ',' that could possibly be in the number (i.e. 2,001,894)
                    aPriceStr = aPriceStr.replace(",", "");
                    bPriceStr = bPriceStr.replace(",", "");

                    Double aPrice = Double.parseDouble(aPriceStr);
                    Double bPrice = Double.parseDouble(bPriceStr);
                    if (aPrice < bPrice) {
                        return -1;
                    } else if (aPrice > bPrice) {
                        return 1;
                    } else {
                        return 0;
                    }
                });

                // The sort above sorts the stocks by ascending price.
                // Reverse so that stocks with the highest price are at the top of the list.
                Collections.reverse(halfStocks);

                recyclerView.getAdapter().notifyItemRangeChanged(0, recyclerView.getAdapter().getItemCount() - 1);
                return true;

            case R.id.sortByPercentChangeMenuItem:
                halfStocks.sort((HalfStock a, HalfStock b) -> {
                    String aPercentChangeStr = substringBetween(a.getStockStat("Daily Price Change").getValue(), "(", "%)");
                    String bPercentChangeStr = substringBetween(b.getStockStat("Daily Price Change").getValue(), "(", "%)");

                    // Remove off '+' or '-' at start of string
                    aPercentChangeStr = aPercentChangeStr.substring(1);
                    bPercentChangeStr = bPercentChangeStr.substring(1);

                    Double aPercentChange = Double.parseDouble(aPercentChangeStr);
                    Double bPercentChange = Double.parseDouble(bPercentChangeStr);

                    // Ignore sign, want stocks with the largest percent change (magnitude only)
                    aPercentChange = Math.abs(aPercentChange);
                    bPercentChange = Math.abs(bPercentChange);

                    if (aPercentChange < bPercentChange) {
                        return -1;
                    } else if (aPercentChange > bPercentChange) {
                        return 1;
                    } else {
                        return 0;
                    }
                });

                // The sort above sorts the stocks by ascending daily percent change (magnitude).
                // Reverse so that stocks with the highest daily percent change are at the top of the list.
                Collections.reverse(halfStocks);

                recyclerView.getAdapter().notifyItemRangeChanged(0, recyclerView.getAdapter().getItemCount());
                return true;

            case R.id.shuffleMenuItem:
                Collections.shuffle(halfStocks);
                recyclerView.getAdapter().notifyItemRangeChanged(0, recyclerView.getAdapter().getItemCount());
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * @return An ArrayList of tickers of the stocks in halfStocks, in the order that they are in halfStocks.
     */
    private ArrayList<String> getTickersInHalfStocks() {
        ArrayList<String> tickers = new ArrayList<>(halfStocks.size());
        for (HalfStock halfStock : halfStocks) {
            tickers.add(halfStock.getTicker());
        }
        return tickers;
    }

}
