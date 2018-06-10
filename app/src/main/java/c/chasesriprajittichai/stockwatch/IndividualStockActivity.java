package c.chasesriprajittichai.stockwatch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.robinhood.spark.SparkView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import c.chasesriprajittichai.stockwatch.listeners.DownloadIndividualStockTaskListener;
import c.chasesriprajittichai.stockwatch.stocks.AdvancedStock;
import c.chasesriprajittichai.stockwatch.stocks.AfterHoursStock;
import c.chasesriprajittichai.stockwatch.stocks.BasicStock;
import c.chasesriprajittichai.stockwatch.stocks.PremarketStock;

import static c.chasesriprajittichai.stockwatch.stocks.BasicStock.State.AFTER_HOURS;
import static c.chasesriprajittichai.stockwatch.stocks.BasicStock.State.CLOSED;
import static c.chasesriprajittichai.stockwatch.stocks.BasicStock.State.OPEN;
import static c.chasesriprajittichai.stockwatch.stocks.BasicStock.State.PREMARKET;
import static java.lang.Double.parseDouble;
import static org.apache.commons.lang3.StringUtils.substring;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.apache.commons.lang3.StringUtils.substringBetween;


public final class IndividualStockActivity extends AppCompatActivity implements DownloadIndividualStockTaskListener {

    private static final class DownloadStockDataTask extends AsyncTask<Void, Integer, AdvancedStock> {

        private final String mticker;
        private final WeakReference<DownloadIndividualStockTaskListener> mcompletionListener;

        private DownloadStockDataTask(final String ticker, final DownloadIndividualStockTaskListener completionListener) {
            mticker = ticker;
            mcompletionListener = new WeakReference<>(completionListener);
        }

        @Override
        protected AdvancedStock doInBackground(final Void... params) {
            /* Get chart data. */
            final Document multiDoc;
            try {
                multiDoc = Jsoup.connect("https://www.marketwatch.com/investing/multi?tickers=" + mticker).get();
            } catch (final IOException ioe) {
                Log.e("IOException", ioe.getLocalizedMessage());
                return new AdvancedStock(OPEN, "", "", -1, -1, -1, "", new ArrayList<>());
            }

            /* Some stocks have no chart data. If this is the case, chart_prices will be an
             * empty array list. */
            final ArrayList<Double> chart_prices = new ArrayList<>();

            final Element multiQuoteRoot = multiDoc.selectFirst("div[class~=section activeQuote bgQuote (down|up)?]");
            final Element javascriptElmnt = multiQuoteRoot.selectFirst(":root > div.intradaychart > script[type=text/javascript]");

            /* If there is no chart data, javascriptElmnt element still exists in the HTML and
             * there is still some javascript code in javascriptElmnt.toString(). There is just no
             * chart data embedded in the code. This means that the call to substringBetween() on
             * javascriptElmnt.toString() will return null, because no substring between the
             * open and close parameters (substringBetween() parameters) exists. */
            final String javascriptStr = substringBetween(javascriptElmnt.toString(), "Trades\":[", "]");
            if (javascriptStr != null) {
                /* javascriptStr is in CSV format. Values in javascriptStr could be "null". Values
                 * do not contain ','. If null values are found, replace them with the last
                 * non-null value. */
                final String[] chart_priceStrs = javascriptStr.split(",");

                // Init chart_prevPrice as first non-null value
                double chart_prevPrice = -1;
                boolean chart_priceFound = false;
                for (final String s : chart_priceStrs) {
                    if (!s.equals("null")) {
                        chart_prevPrice = parseDouble(s);
                        chart_priceFound = true;
                        break;
                    }
                }
                // Fill chart_prices
                if (chart_priceFound) {
                    chart_prices.ensureCapacity(chart_priceStrs.length);
                    for (int i = 0; i < chart_priceStrs.length; i++) {
                        if (!chart_priceStrs[i].equals("null")) {
                            chart_prices.add(i, parseDouble(chart_priceStrs[i]));
                            chart_prevPrice = chart_prices.get(i); // Update prevPrice
                        } else {
                            chart_prices.add(i, chart_prevPrice);
                        }
                    }
                }
            }

            final Document individualDoc;
            final AdvancedStock ret;
            /* Get non-chart data. */
            try {
                individualDoc = Jsoup.connect("https://www.marketwatch.com/investing/stock/" + mticker).get();
            } catch (final IOException ioe) {
                Log.e("IOException", ioe.getLocalizedMessage());
                return new AdvancedStock(OPEN, "", "", -1, -1, -1, "", new ArrayList<>());
            }

            final Element quoteRoot = individualDoc.selectFirst("body[role=document] > div[data-symbol=" + mticker + "]");

            final Element regionFixed = quoteRoot.selectFirst("div[class=content-region region--fixed]");

            final Element nameElmnt = regionFixed.selectFirst("div[class=column column--full company] div[class=row] > h1[class=company__name]");
            final String name = nameElmnt.text();

            final Element intraday = regionFixed.selectFirst("div[class=template template--aside] div[class=element element--intraday]");
            final Element intradayData = intraday.selectFirst("div[class=intraday__data]");

            final Element icon = intraday.selectFirst("small[class~=intraday__status status--(before|open|after|closed)] > i[class^=icon]");
            final String stateStr = icon.nextSibling().toString();
            final BasicStock.State state;
            switch (stateStr.toLowerCase(Locale.US)) {
                case "before the bell": // Multiple stock page uses this
                case "premarket": // Individual stock page uses this
                    state = PREMARKET;
                    break;
                case "open":
                    state = OPEN;
                    break;
                case "after hours":
                    state = AFTER_HOURS;
                    break;
                case "market closed": // Multiple stock view site uses this
                case "closed":
                    state = CLOSED;
                    break;
                default:
                    state = OPEN; /** Create error case (error state). */
                    break;
            }

            final Element priceElmnt, changePointElmnt, changePercentElmnt, close_intradayElmnt,
                    close_priceElmnt, close_changePointElmnt, close_changePercentElmnt;
            final Elements close_tableCells;
            final double price, changePoint, changePercent, close_price, close_changePoint, close_changePercent;
            // Parsing of certain data varies depending on the state of the stock.
            switch (state) {
                case PREMARKET: {
                    priceElmnt = intradayData.selectFirst("h3.intraday__price > bg-quote[class^=value]");
                    changePointElmnt = intradayData.selectFirst("span.change--point--q > bg-quote[field=change]");
                    changePercentElmnt = intradayData.selectFirst("span.change--percent--q > bg-quote[field=percentchange]");

                    close_intradayElmnt = intraday.selectFirst("div.intraday__close");
                    close_tableCells = close_intradayElmnt.select("tr.table__row > td[class^=table__cell]");
                    close_priceElmnt = close_tableCells.get(0);
                    close_changePointElmnt = close_tableCells.get(1);
                    close_changePercentElmnt = close_tableCells.get(2);

                    // Remove ',' or '%' that could be in strings
                    price = parseDouble(priceElmnt.text().replaceAll("[^0-9.]+", ""));
                    changePoint = parseDouble(changePointElmnt.text().replaceAll("[^0-9.-]+", ""));
                    changePercent = parseDouble(changePercentElmnt.text().replaceAll("[^0-9.-]+", ""));
                    close_price = parseDouble(close_priceElmnt.text().replaceAll("[^0-9.]+", ""));
                    close_changePoint = parseDouble(close_changePointElmnt.text().replaceAll("[^0-9.-]+", ""));
                    close_changePercent = parseDouble(close_changePercentElmnt.text().replaceAll("[^0-9.-]+", ""));
                    break;
                }
                case OPEN: {
                    priceElmnt = intradayData.selectFirst("h3.intraday__price > bg-quote[class^=value]");
                    changePointElmnt = intradayData.selectFirst("bg-quote[class^=intraday__change] > span.change--point--q > bg-quote[field=change]");
                    changePercentElmnt = intradayData.selectFirst("bg-quote[class^=intraday__change] > span.change--percent--q > bg-quote[field=percentchange]");

                    // Remove ',' or '%' that could be in strings
                    price = parseDouble(priceElmnt.text().replaceAll("[^0-9.]+", ""));
                    changePoint = parseDouble(changePointElmnt.text().replaceAll("[^0-9.-]+", ""));
                    changePercent = parseDouble(changePercentElmnt.text().replaceAll("[^0-9.-]+", ""));

                    // Initialize unused data
                    close_price = 0;
                    close_changePoint = 0;
                    close_changePercent = 0;
                    break;
                }
                case AFTER_HOURS: {
                    priceElmnt = intradayData.selectFirst("h3.intraday__price > bg-quote[class^=value]");
                    changePointElmnt = intradayData.selectFirst("span.change--point--q > bg-quote[field=change]");
                    changePercentElmnt = intradayData.selectFirst("span.change--percent--q > bg-quote[field=percentchange]");

                    close_intradayElmnt = intraday.selectFirst("div.intraday__close");
                    close_tableCells = close_intradayElmnt.select("tr.table__row > td[class^=table__cell]");
                    close_priceElmnt = close_tableCells.get(0);
                    close_changePointElmnt = close_tableCells.get(1);
                    close_changePercentElmnt = close_tableCells.get(2);

                    // Remove ',' or '%' that could be in strings
                    price = parseDouble(priceElmnt.text().replaceAll("[^0-9.]+", ""));
                    changePoint = parseDouble(changePointElmnt.text().replaceAll("[^0-9.-]+", ""));
                    changePercent = parseDouble(changePercentElmnt.text().replaceAll("[^0-9.-]+", ""));
                    close_price = parseDouble(close_priceElmnt.text().replaceAll("[^0-9.]+", ""));
                    close_changePoint = parseDouble(close_changePointElmnt.text().replaceAll("[^0-9.-]+", ""));
                    close_changePercent = parseDouble(close_changePercentElmnt.text().replaceAll("[^0-9.-]+", ""));
                    break;
                }
                case CLOSED: {
                    priceElmnt = intradayData.selectFirst("h3.intraday__price > span.value");
                    changePointElmnt = intradayData.selectFirst("bg-quote[class^=intraday__change] > span.change--point--q");
                    changePercentElmnt = intradayData.selectFirst("bg-quote[class^=intraday__change] > span.change--percent--q");

                    // Remove ',' or '%' that could be in strings
                    price = parseDouble(priceElmnt.text().replaceAll("[^0-9.]+", ""));
                    changePoint = parseDouble(changePointElmnt.text().replaceAll("[^0-9.-]+", ""));
                    changePercent = parseDouble(changePercentElmnt.text().replaceAll("[^0-9.-]+", ""));

                    // Initialize unused data
                    close_price = 0;
                    close_changePoint = 0;
                    close_changePercent = 0;
                    break;
                }
                default: { /** Create error state. */
                    price = -1;
                    changePoint = -1;
                    changePercent = -1;
                    close_price = -1;
                    close_changePoint = -1;
                    close_changePercent = -1;
                }
            }

            final Element regionPrimary = quoteRoot.selectFirst("div.content-region.region--primary");

            /* Some stocks don't have a description. If there is no description, then
             * descriptionElmnt does not exist. */
            final Element descriptionElmnt = regionPrimary.selectFirst(":root > div.template.template--primary > div.column.column--full > div[class*=description] > p.description__text");
            final String description;
            if (descriptionElmnt != null) {
                /* There's a button at the bottom of the description that is a link to the profile
                 * tab of the individual stock site. The button's title shows up as part of the
                 * text - remove it. */
                description = substringBefore(descriptionElmnt.text(), " (See Full Profile)");
            } else {
                description = "";
            }

            switch (state) {
                case PREMARKET:
                    ret = new PremarketStock(state, mticker, name, price, changePoint,
                            changePercent, close_price, close_changePoint, close_changePercent,
                            description, chart_prices);
                    break;
                case OPEN:
                    ret = new AdvancedStock(state, mticker, name, price, changePoint, changePercent,
                            description, chart_prices);
                    break;
                case AFTER_HOURS:
                    ret = new AfterHoursStock(state, mticker, name, price, changePoint,
                            changePercent, close_price, close_changePoint, close_changePercent,
                            description, chart_prices);
                    break;
                case CLOSED:
                    ret = new AdvancedStock(state, mticker, name, price, changePoint, changePercent,
                            description, chart_prices);
                    break;
                default:
                    ret = null;
                    break;
            }

            return ret;
        }

        @Override
        protected void onPostExecute(final AdvancedStock stock) {
            mcompletionListener.get().onDownloadIndividualStockTaskCompleted(stock);
        }
    }

    @BindView(R.id.sparkView_individual) SparkView msparkView;
    @BindView(R.id.textView_scrub_individual) TextView mscrubInfo;
    @BindView(R.id.divider_sparkViewToStats_individual) View msparkViewToStatsDivider;
    @BindView(R.id.textView_state_individual) TextView mstate;
    @BindView(R.id.textView_price_individual) TextView mprice;
    @BindView(R.id.textView_changePoint_individual) TextView mchangePoint;
    @BindView(R.id.textView_changePercent_individual) TextView mchangePercent;
    @BindView(R.id.textView_close_price_individual) TextView mclose_price;
    @BindView(R.id.textView_close_changePoint_individual) TextView mclose_changePoint;
    @BindView(R.id.textView_close_changePercent_individual) TextView mclose_changePercent;
    @BindView(R.id.divider_statisticsToDescription_individual) View mstatsToDescriptionDivider;
    @BindView(R.id.textView_description_individual) TextView mdescriptionTextView;

    private String mticker; // Needed to create mstock
    private AdvancedStock mstock;
    private boolean mwasInFavoritesInitially;
    private boolean misInFavorites;
    private SparkViewAdapter msparkViewAdapter;
    private SharedPreferences mpreferences;

    @Override
    public void onDownloadIndividualStockTaskCompleted(final AdvancedStock stock) {
        mstock = stock;

        if (!getTitle().equals(mstock.getName())) {
            setTitle(mstock.getName());
        }

        if (mstock.getState() == OPEN || mstock.getState() == CLOSED) {
            mclose_price.setVisibility(View.GONE);
            mclose_changePoint.setVisibility(View.GONE);
            mclose_changePercent.setVisibility(View.GONE);
        }

        if (!mstock.getyData().isEmpty()) {
            msparkViewAdapter.setyData(mstock.getyData());
            msparkViewAdapter.notifyDataSetChanged();
            mscrubInfo.setText(getString(R.string.double2dec, mstock.getPrice())); // Init text view
            msparkView.setScrubListener((final Object valueObj) -> {
                if (valueObj == null) {
                    mscrubInfo.setText(getString(R.string.double2dec, mstock.getPrice()));
                    int color_deactivated = getResources().getColor(R.color.colorAccentTransparent, getTheme());
                    mscrubInfo.setTextColor(color_deactivated);
                } else {
                    mscrubInfo.setText(getString(R.string.double2dec, (double) valueObj));
                    int color_activated = getResources().getColor(R.color.colorAccent, getTheme());
                    mscrubInfo.setTextColor(color_activated);
                }
            });
            msparkViewToStatsDivider.setVisibility(View.VISIBLE); // Initialized as GONE in xml
        } else {
            msparkView.setVisibility(View.GONE);
            mscrubInfo.setVisibility(View.GONE);
        }

        mstate.setText(getString(R.string.string_colon_string, "State", mstock.getState().toString()));
        mprice.setText(getString(R.string.string_colon_double2dec, "Price", mstock.getPrice()));
        mchangePoint.setText(getString(R.string.string_colon_double2dec, "Point Change", mstock.getChangePoint()));
        mchangePercent.setText(getString(R.string.string_colon_double2dec_percent, "Percent Change", mstock.getChangePercent()));
        if (mstock instanceof AfterHoursStock) {
            final AfterHoursStock ahStock = (AfterHoursStock) mstock;
            mclose_price.setText(getString(R.string.string_colon_double2dec, "Price at Close", ahStock.getClose_price()));
            mclose_changePoint.setText(getString(R.string.string_colon_double2dec, "Point Change at Close", ahStock.getClose_changePoint()));
            mclose_changePercent.setText(getString(R.string.string_colon_double2dec_percent, "Percent Change at Close", ahStock.getClose_changePercent()));
        } else if (mstock instanceof PremarketStock) {
            final PremarketStock ahStock = (PremarketStock) mstock;
            mclose_price.setText(getString(R.string.string_colon_double2dec, "Price at Close", ahStock.getClose_price()));
            mclose_changePoint.setText(getString(R.string.string_colon_double2dec, "Point Change at Close", ahStock.getClose_changePoint()));
            mclose_changePercent.setText(getString(R.string.string_colon_double2dec_percent, "Percent Change at Close", ahStock.getClose_changePercent()));
        }

        if (!mstock.getDescription().isEmpty()) {
            mdescriptionTextView.setText(mstock.getDescription());
            mstatsToDescriptionDivider.setVisibility(View.VISIBLE); // Initialized as GONE in xml
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock);
        setTitle(""); // Show empty title now, company name will be shown (in onPostExecute())
        ButterKnife.bind(this);
        mpreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mticker = getIntent().getStringExtra("Ticker");

        // Start task ASAP
        final DownloadStockDataTask task = new DownloadStockDataTask(mticker, this);
        task.execute();

        misInFavorites = getIntent().getBooleanExtra("Is in favorites", false);
        mwasInFavoritesInitially = misInFavorites;

        msparkViewAdapter = new SparkViewAdapter(new ArrayList<>()); // Init as empty
        msparkView.setAdapter(msparkViewAdapter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (misInFavorites != mwasInFavoritesInitially) {
            // If the star status (favorite status) has changed
            if (mwasInFavoritesInitially) {
                removeStockFromPreferences();
            } else {
                addStockToPreferences();
            }

            /* The activity has paused. Update the favorites status.
             * The condition to be able to edit Tickers CSV and Data CSV are dependent on whether
             * or not the favorites status has changed. */
            mwasInFavoritesInitially = misInFavorites;
        }
    }

    @Override
    public void onBackPressed() {
        /* The parent (non-override) onBackPressed() does not create a new HomeActivity. So when we
         * go back back to HomeActivity, the first function called is onResume(); onCreate() is not
         * called. HomeActivity depends on the property that Tickers CSV and Data CSV are not
         * changed in between calls to HomeActivity.onPause() and HomeActivity.onResume(). Tickers
         * CSV and Data CSV can be changed within this class. Therefore, if we don't start a new
         * HomeActivity in this function, then it is possible that Tickers CSV and Data CSV are
         * changed in between calls to HomeActivity.onResume() and HomeActivity.onPause(). */
        final Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_stock_activity, menu);
        final MenuItem starItem = menu.findItem(R.id.starMenuItem);
        starItem.setIcon(misInFavorites ? R.drawable.star_on : R.drawable.star_off);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.starMenuItem:
                misInFavorites = !misInFavorites; // Toggle
                item.setIcon(misInFavorites ? R.drawable.star_on : R.drawable.star_off);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Adds mstock to mpreferences; adds mstock's ticker to Tickers CSV and and adds mstock's data
     * to Data CSV. mstock is added to the front of each preference string, meaning that mstock
     * is inserted at the top of the list of stocks. This function does not check if mstock is
     * already in mpreferences before adding mstock.
     */
    private void addStockToPreferences() {
        final String tickersCSV = mpreferences.getString("Tickers CSV", "");
        final String dataCSV = mpreferences.getString("Data CSV", "");
        final String dataStr;

        if (!tickersCSV.isEmpty()) {
            mpreferences.edit().putString("Tickers CSV", mticker + ',' + tickersCSV).apply();
            dataStr = mstock.getState().toString() + ',' + mstock.getPrice() + ',' +
                    mstock.getChangePoint() + ',' + mstock.getChangePercent() + ',';
            mpreferences.edit().putString("Data CSV", dataStr + dataCSV).apply();
        } else {
            mpreferences.edit().putString("Tickers CSV", mticker).apply();
            dataStr = mstock.getState().toString() + ',' + mstock.getPrice() + ',' +
                    mstock.getChangePoint() + ',' + mstock.getChangePercent();
            mpreferences.edit().putString("Data CSV", dataStr).apply();
        }
    }

    /**
     * Removes mstock from mpreferences; removes mstock's ticker from Tickers CSV and removes
     * mStock's data from Data CSV.
     */
    private void removeStockFromPreferences() {
        final String tickersCSV = mpreferences.getString("Tickers CSV", "");
        final String[] tickerArr = tickersCSV.split(","); // "".split(",") returns {""}

        if (!tickerArr[0].isEmpty()) {
            final ArrayList<String> tickerList = new ArrayList<>(Arrays.asList(tickerArr));
            final ArrayList<String> dataList = new ArrayList<>(Arrays.asList(
                    mpreferences.getString("Data CSV", "").split(",")));

            final int tickerNdx = tickerList.indexOf(mstock.getTicker());
            tickerList.remove(tickerNdx);
            mpreferences.edit().putString("Tickers CSV", String.join(",", tickerList)).apply();

            // 4 data elements per 1 ticker. DataNdx is the index of the first element to delete.
            final int dataNdx = tickerNdx * 4;
            for (int deleteCount = 1; deleteCount <= 4; deleteCount++) { // Delete 4 data elements
                dataList.remove(dataNdx);
            }
            mpreferences.edit().putString("Data CSV", String.join(",", dataList)).apply();
        }
    }
}
