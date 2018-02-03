package com.happy;

import android.util.Pair;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.transform.Result;

/**
 * Created by Tal on 27/01/2018.
 */

public class BestFit
{
    static class MutablePair<T1, T2>
    {
        public T1 first;
        public T2 second;
        public MutablePair(T1 first, T2 second)
        {
            this.first = first;
            this.second = second;
        }
        public MutablePair()
        {

        }

        public String toString()
        {
            return "("+first.toString() + ", " + second.toString()+")";
        }
    }
    static class ResultTree
    {
        enum ResultType
        {
            OR,
            AND,
            ANY,
        }

        public ResultTree(int from, int to, ResultType type)
        {
            this.from = from;
            this.to = to;
            this.type = type;
        }
        int from;
        int to;
        ResultType type;
        ArrayList<ResultTree> children = new ArrayList<>();

        public String toString()
        {
            String ret = "("+from + " " + to+" "+type+"): {";
            boolean first = true;
            for(ResultTree child : children)
            {
                if(!first)
                {
                    ret += ", ";
                }
                ret += child.toString();
                first = false;
            }
            ret += "}";
            return ret;
        }
    }

    ArrayList<DynamicView> templates;

    public BestFit(ArrayList<DynamicView> views)
    {
        templates = views;
    }

    public ArrayList<DynamicView> findAll(DynamicView input, String type)
    {
        ArrayList<DynamicView> storage = new ArrayList<>();
        findAll(input, type, storage, false);
        return storage;
    }
    void findAll(DynamicView input, String type, ArrayList<DynamicView> storage, boolean includeself)
    {
        if(includeself)
        {
            if(input.type.equals(type) || input.type.equals("*"))
            {
                storage.add(input);
            }
        }

        for(DynamicView child : input.children)
        {
            findAll(child, type, storage, true);
        }
    }

    public ResultTree possible(DynamicView input, DynamicView template, boolean root)
    {
        if(template.type.equals("*") && !root)
        {
            return new ResultTree(0, 0, ResultTree.ResultType.ANY);
        }
        ArrayList<DynamicView> results = findAll(template, input.type); //without root
        ArrayList<ResultTree> ret = new ArrayList<>();
        for(DynamicView result : results)
        {
            if(input.children.isEmpty())
            {
                ret.add(new ResultTree(result.getId() == 0 ? 0 : input.getId(), result.getId(), ResultTree.ResultType.OR));
            }
            else
            {
                ArrayList<ResultTree> midret = new ArrayList<>();
                boolean deadend = false;
                for(DynamicView child : input.children)
                {
                    ResultTree subret = possible(child, result, false);
                    if(subret.type != ResultTree.ResultType.ANY)
                    {
                        if (subret.children.isEmpty())
                        {
                            deadend = true;
                            break;
                        }
                        else
                        {
                            midret.add(subret);
                        }
                    }
                }
                if(deadend)
                {
                    continue;
                }
                ResultTree newtree = new ResultTree(result.getId() == 0 ? 0 : input.getId(), result.getId(), ResultTree.ResultType.AND);
                newtree.children.addAll(midret);
                ret.add(newtree);
            }
        }
        ResultTree tree = new ResultTree(0, 0, ResultTree.ResultType.OR);
        tree.children.addAll(ret);
        return tree;
    }

    public MutablePair<Integer, ResultTree> bestRoute(ResultTree tree)
    {
        ResultTree me = new ResultTree(tree.from, tree.to, ResultTree.ResultType.OR);
        MutablePair<Integer, ResultTree> best = null;
        for(ResultTree child : tree.children)
        {
            MutablePair<Integer, ResultTree> runner = bestRoute(child);
            if(tree.type == ResultTree.ResultType.AND)
            {
                me.children.add(runner.second);
            }
            if(best == null || runner.first > best.first)
            {
                best = runner;
            }
        }

        int depth = 1;
        if(best != null)
        {
            if (tree.type == ResultTree.ResultType.OR)
            {
                me.children.add(best.second);
                depth = best.first;
            }
            else
            {
                depth += best.first;
            }
        }
        return new MutablePair<>(depth, me);
    }

    public MutablePair<Integer, Integer> treelen(ResultTree tree)
    {
        MutablePair<Integer, Integer> storage = new MutablePair<>(0, 0);
        treelen(tree, storage);
        return storage;
    }
    public void treelen(ResultTree tree, MutablePair<Integer, Integer> storage)
    {
        storage.first += 1;
        if(tree.from != 0)
        {
            storage.second += 1;
        }

        for(ResultTree child : tree.children)
        {
            treelen(child, storage);
        }
    }

    public Pair<DynamicView, HashMap<Integer, Integer>> bestFit(DynamicView input)
    {
        MutablePair<Integer, Integer> bestLen = null;
        ResultTree best = null;
        DynamicView bestTemplate = null;
        for(DynamicView template : templates)
        {
            ResultTree pos = possible(input, template, true);
            ResultTree runner = bestRoute(pos).second;
            MutablePair<Integer, Integer> len = treelen(runner);
            //no best       ||       fit more of the input   ||   fit the same input          but less crowded in template
            if(best == null || (len.second > bestLen.second) || (len.second.equals(bestLen.second) && len.first < bestLen.first))
            {
                best = runner;
                bestTemplate = template;
                bestLen = len;
            }
        }

        HashMap<Integer, Integer> lst = makeList(best);
        if(lst.isEmpty())
        {
            return null;
        }
        return new Pair<>(bestTemplate, lst);
    }


    HashMap<Integer, Integer> makeList(ResultTree possible)
    {
        HashMap<Integer, Integer> storage = new HashMap<>();
        makeList(possible, storage);
        return storage;
    }
    void makeList(ResultTree possible, HashMap<Integer, Integer> storage)
    {
        if(possible.from != 0)
        {
            if(storage.containsKey(possible.from))
            {
                throw new IllegalArgumentException(storage + " already contains " + possible.from);
            }
            storage.put(possible.from, possible.to);
        }
        for(ResultTree child : possible.children)
        {
            makeList(child, storage);
        }
    }
}
