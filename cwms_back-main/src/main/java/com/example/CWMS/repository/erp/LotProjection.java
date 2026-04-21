package com.example.CWMS.repository.erp;

/**
 * Projection JPA pour findLotWithDesignation.
 *
 * POURQUOI une projection et pas Object[] ?
 * → SQL Server via Hibernate retourne un Object[] dont l'ordre
 *   dépend du driver/version. Accéder à row[4] sur un tableau de
 *   taille 1 = ArrayIndexOutOfBoundsException.
 * → Avec une projection, chaque colonne est mappée par son ALIAS SQL
 *   (AS t_cwar, AS qty...). L'ordre n'a aucune importance.
 */
public interface LotProjection {
    String getT_cwar();  // Magasin        — t_cwar AS t_cwar
    String getT_loca();  // Emplacement    — t_loca AS t_loca
    String getT_item();  // Code article   — t_item AS t_item
    String getT_clot();  // Numéro de lot  — t_clot AS t_clot
    Double getQty();     // Quantité dispo — CAST(t_qhnd AS FLOAT) AS qty
}