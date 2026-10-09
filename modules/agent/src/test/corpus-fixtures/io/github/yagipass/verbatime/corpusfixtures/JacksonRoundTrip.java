package io.github.yagipass.verbatime.corpusfixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class JacksonRoundTrip {

  public static final class Order {

    private int id;

    private List<String> items;

    private Map<String, Double> totals;

    public int getId() {
      return id;
    }

    public void setId(int id) {
      this.id = id;
    }

    public List<String> getItems() {
      return items;
    }

    public void setItems(List<String> items) {
      this.items = items;
    }

    public Map<String, Double> getTotals() {
      return totals;
    }

    public void setTotals(Map<String, Double> totals) {
      this.totals = totals;
    }
  }

  private JacksonRoundTrip() {}

  public static String run() throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    Order in = new Order();
    in.setId(7);
    in.setItems(List.of("tea", "milk"));
    Map<String, Double> totals = new TreeMap<>();
    totals.put("net", 3.5);
    totals.put("tax", 0.35);
    in.setTotals(totals);
    String json = mapper.writeValueAsString(in);
    Order back = mapper.readValue(json, Order.class);
    return json + " -> " + mapper.writeValueAsString(back);
  }
}
