package com.appy;

import androidx.fragment.app.FragmentTransaction;

public abstract class FragmentParent extends MyFragment
{
    public static final String FRAGMENT_TAG = "FRAGMENT";

    public void switchTo(ChildFragment fragment, boolean noBackStack)
    {
        fragment.setParent(this);

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.setCustomAnimations(
                R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                R.animator.slide_in_from_left, R.animator.slide_out_to_right);
        transaction.replace(R.id.configs_container, fragment, FRAGMENT_TAG);
        if (getChildFragmentManager().findFragmentByTag(FRAGMENT_TAG) != null && !noBackStack)
        {
            transaction.addToBackStack(null);
        }
        transaction.commitAllowingStateLoss();
    }

    public void finishActivity()
    {
        if (getActivity() != null)
        {
            getActivity().finish();
        }
    }

    public DictObj.Dict getDict()
    {
        return new DictObj.Dict();
    }
    public void updateDict()
    {

    }

    public static abstract class ChildFragment extends MyFragment
    {
        FragmentParent parent;
        public void setParent(FragmentParent parent)
        {
            this.parent = parent;
        }
    }
}
