package com.example.CWMS.iservice;

import com.example.CWMS.model.cwms.Site;

import java.util.List;

public interface SiteService {
    List<Site> getAllSites();
    Site getSiteById(Integer id);
    Site createSite(Site site);
    Site updateSite(Integer id, Site site);
    void deleteSite(Integer id);
}