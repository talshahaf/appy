package com.happy;

import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.transform.Result;

/**
 * Created by Tal on 27/01/2018.
 */

public class BestFit
{
    static class ResultTree
    {
        public ResultTree(int from, int to, boolean or)
        {
            this.from = from;
            this.to = to;
            this.or = or;
        }
        int from;
        int to;
        boolean or;
        ArrayList<ResultTree> children = new ArrayList<>();
    }

    DynamicView[] templates;

    public BestFit(DynamicView[] views)
    {
        templates = views;
    }

    public ArrayList<DynamicView> findAll(DynamicView input, String type)
    {
        ArrayList<DynamicView> storage = new ArrayList<>();
        findAll(input, type, storage, false);
        return storage;
    }
    void findAll(DynamicView input, String color, ArrayList<DynamicView> storage, boolean includeself)
    {
        if(includeself)
        {
            if(input.type.equals(color))
            {
                storage.add(input);
            }
        }

        for(DynamicView child : input.children)
        {
            findAll(child, color, storage, true);
        }
    }

    public ResultTree possible(DynamicView input, DynamicView template)
    {
        ArrayList<DynamicView> results = findAll(template, input.type); //without root
        ArrayList<ResultTree> ret = new ArrayList<>();
        for(DynamicView result : results)
        {
            if(input.children.isEmpty())
            {
                ret.add(new ResultTree(result.getId(), input.getId(), true));
            }
            else
            {
                ArrayList<ResultTree> midret = new ArrayList<>();
                boolean deadend = false;
                for(DynamicView child : input.children)
                {
                    ResultTree subret = possible(child, result);
                    if(subret.children.isEmpty())
                    {
                        deadend = true;
                        break;
                    }
                    else
                    {
                        midret.add(subret);
                    }
                }
                if(deadend)
                {
                    continue;
                }
                ResultTree newtree = new ResultTree(result.getId(), input.getId(), false);
                newtree.children.addAll(midret);
                ret.add(newtree);
            }
        }
        ResultTree tree = new ResultTree(0, 0, true);
        tree.children.addAll(ret);
        return tree;
    }

    public Pair<Integer, ResultTree> min_route(ResultTree tree)
    {
        ResultTree me = new ResultTree(tree.from, tree.to, true);
        Pair<Integer, ResultTree> best = null;
        for(ResultTree child : tree.children)
        {
            Pair<Integer, ResultTree> runner = min_route(child);
            if(tree.or)
            {
                if(best == null || runner.first < best.first)
                {
                    best = runner;
                }
            }
            else
            {
                me.children.add(runner.second);
                if(best == null || runner.first > best.first)
                {
                    best = runner;
                }
            }
        }

        int depth = 1;
        if(best != null)
        {
            if (tree.or)
            {
                me.children.add(best.second);
                depth = best.first;
            }
            else
            {
                depth += best.first;
            }
        }
        return new Pair<>(depth, me);
    }

    public HashMap<Integer, Integer> best_fit(DynamicView input)
    {
        Pair<Integer, ResultTree> best = null;
        for(DynamicView template : templates)
        {
            Pair<Integer, ResultTree> runner = min_route(possible(input, template));
            if(best == null || runner.first < best.first)
            {
                best = runner;
            }
        }

        HashMap<Integer, Integer> storage = new HashMap<>();
        make_list(best.second, storage);
        if(storage.isEmpty())
        {
            return null;
        }
        return storage;
    }

    void make_list(ResultTree possible, HashMap<Integer, Integer> storage)
    {
        if(possible.to != 0)
        {
            storage.put(possible.from, possible.to);
        }
        for(ResultTree child : possible.children)
        {
            make_list(child, storage);
        }
    }
}
