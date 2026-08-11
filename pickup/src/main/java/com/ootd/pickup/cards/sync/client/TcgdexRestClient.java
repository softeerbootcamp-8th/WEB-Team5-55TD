package com.ootd.pickup.cards.sync.client;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TcgdexRestClient implements TcgdexClient {

  private final RestClient tcgdexRestClient;

  public TcgdexRestClient(@Qualifier("tcgdexApiRestClient") RestClient tcgdexRestClient) {
    this.tcgdexRestClient = tcgdexRestClient;
  }

  @Override
  public List<TcgdexSetSummary> findAllSets() {
    TcgdexSetSummary[] response =
        tcgdexRestClient.get().uri("/sets").retrieve().body(TcgdexSetSummary[].class);
    return response == null ? List.of() : Arrays.asList(response);
  }

  @Override
  public TcgdexSetDetail getSet(String setId) {
    return tcgdexRestClient
        .get()
        .uri("/sets/{setId}", setId)
        .retrieve()
        .body(TcgdexSetDetail.class);
  }

  @Override
  public TcgdexCardDetail getCard(String cardId) {
    return tcgdexRestClient
        .get()
        .uri("/cards/{cardId}", cardId)
        .retrieve()
        .body(TcgdexCardDetail.class);
  }
}
