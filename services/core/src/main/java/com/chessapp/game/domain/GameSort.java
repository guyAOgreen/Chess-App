package com.chessapp.game.domain;

/**
 * The columns a game list may be ordered by.
 *
 * <p>This enum is the whitelist. {@code ORDER BY} cannot be a bound parameter, so a
 * sort field arriving from a request and placed into a query is string
 * concatenation whatever it is called. Being an enum makes an unknown field
 * unrepresentable rather than merely rejected: conversion fails at the HTTP
 * boundary, before a query exists.
 *
 * <p>One value, because {@code played_on} is the only column with a supporting
 * index. Adding another is one constant here and one arm of the switch in
 * {@code GameSearchQuery} — which is the point of having the mechanism from the
 * start.
 */
public enum GameSort {
    PLAYED_ON
}
