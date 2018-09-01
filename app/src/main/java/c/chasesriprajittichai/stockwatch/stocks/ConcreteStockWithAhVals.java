package c.chasesriprajittichai.stockwatch.stocks;

import c.chasesriprajittichai.stockwatch.HomeActivity;


/**
 * Regarding the {@link HomeActivity#rv}, operations on rv will be much faster
 * if rv only contains one type of object. If an element at an index in rv
 * changes type, expensive operations must be done to account for it. For this
 * reason, rv in HomeActivity only uses ConcreteStockWithAhVals, rather than
 * using both {@link ConcreteStock} and ConcreteStockWithAhVals. Additionally,
 * ConcreteStockList has been converted to {@link ConcreteStockWithAhValsList}
 * because of this.
 */
public class ConcreteStockWithAhVals
        extends ConcreteStock
        implements Stock, StockWithAhVals, StockInHomeActivity {

    /**
     * Price in after hours; live price
     */
    private double ahPrice;

    /**
     * The change point that has occurred during after hours trading
     */
    private double ahChangePoint;

    /**
     * The change percent that has occurred during after hours trading
     */
    private double ahChangePercent;

    public ConcreteStockWithAhVals(final State state, final String ticker,
                                   final String name, final double price,
                                   final double changePoint,
                                   final double changePercent,
                                   final double afterHoursPrice,
                                   final double afterHoursChangePoint,
                                   final double afterHoursChangePercent) {
        super(state, ticker, name, price, changePoint, changePercent);
        ahPrice = afterHoursPrice;
        ahChangePoint = afterHoursChangePoint;
        ahChangePercent = afterHoursChangePercent;
    }

    /**
     * Copy constructor. If stock instanceof {@link StockWithAhVals},
     * this ConcreteStockWithAhVals' after hours values are set to the after
     * hours values of stock. Otherwise, this ConcreteStockWithAhVals' after
     * hours values are set to 0.
     *
     * @param stock The Stock to copy
     */
    public ConcreteStockWithAhVals(final Stock stock) {
        super(stock);

        if (stock instanceof StockWithAhVals) {
            final StockWithAhVals ahStock = (StockWithAhVals) stock;
            ahPrice = ahStock.getAfterHoursPrice();
            ahChangePoint = ahStock.getAfterHoursChangePoint();
            ahChangePercent = ahStock.getAfterHoursChangePercent();
        } else {
            ahPrice = 0;
            ahChangePoint = 0;
            ahChangePercent = 0;
        }
    }

    /**
     * Because this is the only non-AdvancedStock used in HomeActivity, this
     * class must be able to represent stocks that should have after hours
     * values and stocks that shouldn't. In the code in HomeActivity and
     * MultiStockRequest, if a ConcreteStockWithAhVals shouldn't have after
     * hours values, the after hours values are set to 0. Checking if a
     * ConcreteStockWithAhVals should have after hours values or not can
     * therefore be done by checking if the after hours price is equal to 0.
     *
     * @return The live price
     */
    @Override
    public final double getLivePrice() {
        return ahPrice == 0 ? getPrice() : ahPrice;
    }

    /**
     * Because this is the only non-AdvancedStock used in HomeActivity, this
     * class must be able to represent stocks that should have after hours
     * values and stocks that shouldn't. In the code in HomeActivity and
     * MultiStockRequest, if a ConcreteStockWithAhVals shouldn't have after
     * hours values, the after hours values are set to 0. Checking if a
     * ConcreteStockWithAhVals should have after hours values or not can
     * therefore be done by checking if the after hours price is equal to 0.
     *
     * @return The live change point
     */
    @Override
    public final double getLiveChangePoint() {
        return ahPrice == 0 ? getChangePoint() : ahChangePoint;
    }

    /**
     * Because this is the only non-AdvancedStock used in HomeActivity, this
     * class must be able to represent stocks that should have after hours
     * values and stocks that shouldn't. In the code in HomeActivity and
     * MultiStockRequest, if a ConcreteStockWithAhVals shouldn't have after
     * hours values, the after hours values are set to 0. Checking if a
     * ConcreteStockWithAhVals should have after hours values or not can
     * therefore be done by checking if the after hours price is equal to 0.
     *
     * @return The live change percent
     */
    @Override
    public final double getLiveChangePercent() {
        return ahPrice == 0 ? getChangePercent() : ahChangePercent;
    }

    /**
     * @return The sum of the change percent during the open trading hours and
     * after hours trading
     */
    @Override
    public final double getNetChangePercent() {
        return getChangePercent() + ahChangePercent;
    }

    /**
     * @return A four element string array containing the {@link
     * StockInHomeActivity}'s {@link Stock.State}, price, change point, and
     * change percent.
     */
    @Override
    public String[] getDataAsArray() {
        String[] data = new String[7];
        data[0] = getState().toString();
        data[1] = String.valueOf(getPrice());
        data[2] = String.valueOf(getChangePoint());
        data[3] = String.valueOf(getChangePercent());
        data[4] = String.valueOf(ahPrice);
        data[5] = String.valueOf(ahChangePoint);
        data[6] = String.valueOf(ahChangePercent);
        return data;
    }

    @Override
    public final double getAfterHoursPrice() {
        return ahPrice;
    }

    @Override
    public final void setAfterHoursPrice(final double afterHoursPrice) {
        ahPrice = afterHoursPrice;
    }

    @Override
    public final double getAfterHoursChangePoint() {
        return ahChangePoint;
    }

    @Override
    public final void setAfterHoursChangePoint(final double afterHoursChangePoint) {
        ahChangePoint = afterHoursChangePoint;
    }

    @Override
    public final double getAfterHoursChangePercent() {
        return ahChangePercent;
    }

    @Override
    public final void setAfterHoursChangePercent(final double afterHoursChangePercent) {
        ahChangePercent = afterHoursChangePercent;
    }

}
